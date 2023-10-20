package com.haleydu.cimoc.source;

import android.util.Base64;
import android.util.Pair;

import com.facebook.common.util.Hex;
import com.google.common.collect.Lists;
import com.haleydu.cimoc.App;
import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.JsonIterator;
import com.haleydu.cimoc.parser.MangaCategory;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.parser.UrlFilter;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.DecryptionUtils;
import com.haleydu.cimoc.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;
import taobe.tec.jcc.JChineseConvertor;

import static com.haleydu.cimoc.core.Manga.getResponseBody;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CopyMH extends MangaParser {
    public static final int TYPE = 26;
    public static final String DEFAULT_TITLE = "拷贝漫画";
    public static final String website = "https://www.copymanga.tv";
    private final String userAgent = "PostmanRuntime/7.29.0";
    private final String aesKey = "xxxmanga.woo.key";

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    public CopyMH(Source source) {
        init(source, new Category());
    }

    @Override
    public Request getSearchRequest(String keyword, int page) {
        String url = "";
        if (page == 1) {
            url = StringUtils.format("https://www.copymanga.tv/api/kb/web/searchs/comics?offset=0&platform=2&limit=12&q=%s&q_type=%s", keyword, "");
            return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
                .build();
        }
        return null;
    }

    @Override
    public String getUrl(String cid) {
        return "https://copymanga.com/h5/details/comic/".concat(cid);
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("copymanga.com", "\\w+", 0));
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        try {
            JSONObject jsonObject = new JSONObject(html);
            return new JsonIterator(jsonObject.getJSONObject("results").getJSONArray("list")) {
                @Override
                protected Comic parse(JSONObject object) {
                    try {
                        JChineseConvertor jChineseConvertor = JChineseConvertor.getInstance();
                        String cid = object.getString("path_word");
                        String title = jChineseConvertor.t2s(object.getString("name"));
                        String cover = object.getString("cover");
                        String author = object.getJSONArray("author").getJSONObject(0).getString("name").trim();
                        return new Comic(TYPE, cid, title, cover, null, author);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = String.format("%s/comic/%s", website, cid);
        return new Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) {
        try {
            Node body = new Node(html);
            String title = body.text(".col-9.comicParticulars-title-right > ul >li > h6");
            String cover = body.getChild(".comicParticulars-left-img.loadingIcon > img").attr("data-src");
            String author = body.text("span.comicParticulars-right-txt >a");
            String intro = body.text(".intro");
            boolean status = isFinish(body.text("body > main > div.container.comicParticulars-title > div > div.col-9.comicParticulars-title-right > ul > li:nth-child(6) > span.comicParticulars-right-txt"));

            String update = body.text(".col-9.comicParticulars-title-right > ul > li:nth-child(5) > span:nth-child(2)");
            if (update == null || update.equals("")) {
                update = "没找到最后更新日期";
            }
            comic.setInfo(title, cover, update, intro, author, status);
            return comic;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Request getChapterRequest(String html, String cid) {
        String url = String.format("%s/comicdetail/%s/chapters", website, cid);
        return new Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .build();
    }

    private  String aesDecrypt(String value, String key, String ivs) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        IvParameterSpec iv = new IvParameterSpec(ivs.getBytes("UTF-8"));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        int size = cipher.getBlockSize();
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

        byte[] hexArry = Hex.decodeHex(value);
        byte[] temp = Base64.encode(hexArry, 0);
        byte[] code = Base64.decode(temp, Base64.DEFAULT);
        return new String(cipher.doFinal(code));
    }

    @Override
    public Request getCategoryRequest(String format, int page) {
        String url = StringUtils.format(format, page);
        return new Request.Builder().url(url).addHeader("Host", "www.copymanga.tv")
                .addHeader("User-Agent", userAgent)
        .build();
    }

    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        Node body = new Node(html);
        for (Node node : body.list(".col-auto.exemptComic_Item")) {
            String cid = node.attr("div > a", "href");
            cid = cid.substring(cid.lastIndexOf("/") + 1);

            String title = node.attr("div:nth-child(2)  >div >a >p", "title");
            String cover = node.attr("div:nth-child(1) > a > img", "data-src");
            String author = node.text(" div:nth-child(2)  >div >span >a");
            list.add(new Comic(TYPE, cid, title, cover, null, author));
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();

        try {
            JSONObject jsonObject = new JSONObject(html);
            String results = jsonObject.getString("results");
            String iv = results.substring(0, 0x10);
            String aesData = results.substring(0x10);
            byte[] hexCode = Hex.decodeHex(aesData);
            String encode = Base64.encodeToString(hexCode, 0, hexCode.length, Base64.NO_WRAP);
            String plain = DecryptionUtils.aesDecrypt(encode, aesKey, iv);

            JSONObject chapterObj = new JSONObject(plain);
            JSONArray array = chapterObj.getJSONObject("groups").getJSONObject("default").getJSONArray("chapters");

            for (int i = 0; i < array.length(); ++i) {
                String title = array.getJSONObject(i).getString("name");
                String path = array.getJSONObject(i).getString("id");
                list.add(new Chapter(Long.parseLong(sourceComic + "000" + i), sourceComic, title, path, "默认"));
            }

        }catch (Exception exception) {
            exception.printStackTrace();
        }

        return Lists.reverse(list);
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/comic/%s/chapter/%s", website,cid, path);
        return new Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new LinkedList<>();
        Node body = new Node(html);
        String data = body.attr("div.imageData", "contentkey");
        String key = aesKey;
        String iv = data.substring(0, 0x10).trim();
        String result = data.substring(0x10).trim();
        byte[] hexCode = Hex.decodeHex(result);
        String encode = Base64.encodeToString(hexCode, 0, hexCode.length, Base64.NO_WRAP);

        try {
            String jsonString = DecryptionUtils.aesDecrypt(encode, key, iv);
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); ++i) {
                Long comicChapter = chapter.getId();
                Long id = Long.parseLong(comicChapter + "000" + i);
                String url = array.getJSONObject(i).getString("url");
                list.add(new ImageUrl(id, comicChapter,i + 1, url, false));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        try {
            JSONObject comicInfo = new JSONObject(html).getJSONObject("results");
            JSONObject body = comicInfo.getJSONObject("comic");
            return body.getString("datetime_updated");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", website);
    }

    private static class Category extends MangaCategory {

        @Override
        public String getFormat(String... args) {
            String path = args[CATEGORY_SUBJECT].concat(" ").trim();
            if (path.isEmpty()) {
                path = String.valueOf(0);
            } else {
                path = path.replaceAll("\\s+", "-");
            }

            String format = StringUtils.format("%s/%s", CopyMH.website, path);
            return format;
        }

        @Override
        protected List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("热门推荐", "recommend?type=3200102&offset=%d&limit=60"));
            return list;
        }
    }
}
