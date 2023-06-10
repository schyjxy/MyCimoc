package com.haleydu.cimoc.source;

import android.util.Log;
import android.util.Pair;

import com.haleydu.cimoc.core.Manga;
import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.MangaCategory;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.NodeIterator;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.Request;

//绅士漫画实现

public class ShenshiManHua extends MangaParser {

    public static final int TYPE = 149;
    public static final String DEFAULT_TITLE = "绅士漫画";
    private  final  String host = "https://www.htmanga4.top";

    public ShenshiManHua(Source source) {
        init(source, new Category());
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException, Exception {
        String url = "";
        if (page == 1) {
            url = StringUtils.format("%s/search/?q=%s&f=_all&s=create_time_DESC&syn=yes", host, keyword);
        }

        return new Request.Builder().url(url).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        return new NodeIterator(body.list(".pic_box")) {
            @Override
            protected Comic parse(Node node) {
                String cid = node.href("a");
                cid = cid.substring(cid.lastIndexOf("-") + 1);
                cid = cid.substring(0, cid.indexOf("."));
                String title = node.attr("a", "title").replace("<em>", "");
                title = title.replace("</em>","").trim();
                String cover = String.format("http:%s", node.attr("a > img", "src"));
                return new Comic(TYPE, cid, title, cover, null, null);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = host.concat("/photos-index-aid-" + cid + ".html");
        return new Request.Builder().url(url).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        String title = body.getChild("#bodywrap > h2").text();
        String cover = "http:" + body.src(".asTBcell.uwthumb > img");
        String author = "佚名";
        if(title.contains("[")){
            author = title.substring(title.indexOf("[") + 1).substring(0, title.indexOf("]") - 1);
        }

        String intro = body.text(".asTBcell.uwconn >p");
        boolean status = true;

        String update = "没有更新日期";
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        int i=0;
        for (Node node : new Node(html).list(".btn:nth-child(3)")) {
            String title = node.text();
            String path = host + node.href();
            Chapter chapter = new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path);
            list.add(chapter);
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/photos-gallery-aid-%s.html", host, cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new LinkedList<ImageUrl>();
        if(html.indexOf("imglist") == -1) {
            return  list;
        }

        String temp = html.substring(html.indexOf("imglist"));
        Pattern pattern = Pattern.compile("//(?<url>[\\w./-]*)\\\\");
        Matcher m = pattern.matcher(temp);
        long id = 0;

        while (m.find()) {
            String url = "http://" +  m.group("url");
            list.add(new ImageUrl(id++, chapter.getId(), (int)id, url, false));
        }
        return list;
    }

    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new LinkedList<>();
        try {
            JSONArray array = new JSONArray(html);
            for (int i = 0; i != array.length(); ++i) {
                try {
                    JSONObject object = array.getJSONObject(i);
                    String cid = object.getString("id");
                    String title = object.getString("title");
                    String cover = object.getString("cover");
                    Long time = object.has("last_updatetime") ? object.getLong("last_updatetime") * 1000 : null;
                    String update = time == null ? null : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
                    String author = object.optString("authors");
                    list.add(new Comic(TYPE, cid, title, cover, update, author));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", host);
    }

    private static class Category extends MangaCategory {

        @Override
        public boolean isComposite() {
            return true;
        }

        @Override
        public String getFormat(String... args) {
            String path = args[CATEGORY_SUBJECT].concat(" ").concat(args[CATEGORY_READER]).concat(" ").concat(args[CATEGORY_PROGRESS])
                    .concat(" ").concat(args[CATEGORY_AREA]).trim();
            if (path.isEmpty()) {
                path = String.valueOf(0);
            } else {
                path = path.replaceAll("\\s+", "-");
            }
            return StringUtils.format("http://v2.api.dmzj.com/classify/%s/%s/%%d.json", path, args[CATEGORY_ORDER]);
        }

        @Override
        public List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("首页", "https://www.htmanga3.top/"));
            return list;
        }

    }
}
