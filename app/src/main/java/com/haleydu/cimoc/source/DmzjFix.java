package com.haleydu.cimoc.source;

import android.annotation.SuppressLint;

import com.haleydu.cimoc.core.Manga;
import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.JsonIterator;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.parser.UrlFilter;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.StringUtils;
import com.haleydu.cimoc.utils.UicodeBackslashU;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.Request;

public class DmzjFix extends MangaParser {
    public static final int TYPE = 100;
    public static final String DEFAULT_TITLE = "动漫之家v2Fix";

    public DmzjFix(Source source) {
        init(source, null);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("manhua.dmzj.com", "/(\\w+)"));
        filter.add(new UrlFilter("m.dmzj.com", "/info/(\\w+).html"));
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException, Exception {
        if (page == 1) {
            String url = StringUtils.format("https://m.dmzj.com/search/%s.html", keyword);
            return new Request.Builder().url(url).build();
        }
        return null;
    }

    @Override
    public String getUrl(String cid) {
        return StringUtils.format("http://m.dmzj.com/info/%s.html", cid);
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        try {
            String JsonString = StringUtils.match("var serchArry=(\\[\\{.*?\\}\\])", html, 1);
            String decodeJsonString = UicodeBackslashU.unicodeToCn(JsonString).replace("\\/", "/");
            return new JsonIterator(new JSONArray(decodeJsonString)) {
                @Override
                protected Comic parse(JSONObject object) {
                    try {
                        String cid = object.getString("id");
                        String title = object.getString("name");
                        String cover = object.getString("cover");
                        cover = "https://images.dmzj.com/" + cover;
                        String author = object.optString("authors");
                        long time = Long.parseLong(object.getString("last_updatetime")) * 1000;
                        String update = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
                        return new Comic(TYPE, cid, title, cover, update, author);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = getUrl(cid);
        return new Request.Builder().url(url).build();
    }

    public Headers getHeader() {
        return Headers.of("Referer", "http://images.dmzj.com/");
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        try {
            Node body = new Node(html);
            String title = body.text("#comicName");
            String cover = body.src("#Cover > img");
            String author = body.text(".txtItme:nth-child(1) > a");
            String intro = body.text(".txtDesc.autoHeight");
            boolean status = isFinish(body.text(".txtItme:nth-child(3) > a:nth-child(4)"));
            String update = body.text(".txtItme:nth-child(4) >span:nth-child(2)");
            if (update == null || update.equals("")) {
                update = "没找到最后更新日期";
            }
            comic.setInfo(title, cover, update, intro, author, status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        try {
            String JsonArrayString = StringUtils.match("initIntroData\\((.*)\\);", html, 1);
            String decodeJsonArrayString = UicodeBackslashU.unicodeToCn(JsonArrayString);
            JSONArray allJsonArray = new JSONArray(decodeJsonArrayString);
            int k=0;
            for (int i=0;i<allJsonArray.length();i++){
                JSONArray JSONArray = allJsonArray.getJSONObject(i).getJSONArray("data");
                String tag = allJsonArray.getJSONObject(i).getString("title");
                for (int j = 0; j != JSONArray.length(); ++j) {
                    JSONObject chapter = JSONArray.getJSONObject(j);
                    String title = chapter.getString("chapter_name");
                    String comic_id = chapter.getString("comic_id");
                    String chapter_id = chapter.getString("id");
                    String path = comic_id + "/" +chapter_id;
                    list.add(new Chapter(Long.parseLong(sourceComic + "000" + k++), sourceComic, tag+" "+title, path));
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;


    }

    @Override
    public Request getImagesRequest(String cid, String path) {

        String url = StringUtils.format("https://m.dmzj.com/chapinfo/%s.html", path.replace("x", ""));
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new LinkedList<>();
        JSONArray root = new JSONObject(html).getJSONArray("page_url");
        String flag = chapter.getId().toString().replace(chapter.getSourceComic().toString(), "");
        for (int i = 0; i < root.length(); i++) {
            Long comicChapter = chapter.getId();
            String url = root.getString(i);
            Long id = Long.parseLong(comicChapter + "000" + i);

            if (flag.startsWith("001")) {
                url = url.replace("dmzj", "dmzj1");

            }
            list.add(new ImageUrl(id, comicChapter, i + 1, url, false));


        }


        return list;
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public String parseCheck(String html) {
        try {
            String update = new JSONObject(html).getJSONObject("data").getString("last_updatetime");
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(Integer.parseInt(update) * 1000));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;

    }

}
