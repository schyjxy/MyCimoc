package com.haleydu.cimoc.source;

import android.util.Pair;

import com.google.common.collect.Lists;
import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.MangaCategory;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.NodeIterator;
import com.haleydu.cimoc.parser.Parser;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.parser.UrlFilter;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.StringUtils;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.Request;

public class JMTT extends MangaParser {

    public static final int TYPE = 72;
    public static final String DEFAULT_TITLE = "禁漫天堂";
    public static final String baseUrl = "https://18comic-now.net/"; //https://cm365.xyz/7MJX9t
    private final String userAgent = "PostmanRuntime/7.29.0";

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    public JMTT(Source source) {
        init(source, new  Category());
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException {
        if (page != 1) return null;
        String url = StringUtils.format(baseUrl + "/search/photos?search_query=%s&main_tag=0", keyword);
        return new Request.Builder().url(url).addHeader("User-Agent", userAgent).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        return new NodeIterator(body.list("div#wrapper > div.container > div.row > div > div.row > div")) {
            @Override
            protected Comic parse(Node node) {
                final String cid = node.href("div.thumb-overlay > a");
                final String title = node.text("span.video-title");
                final String cover = node.attr("div.thumb-overlay > a > img", "data-original");
                final String update = node.text("div.video-views");
                final String author = "";
                return new Comic(TYPE, cid, title, cover, update, author);
            }
        };
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + cid;
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("https://18comic.vip"));
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = baseUrl + cid;
        return new Request.Builder().url(url).addHeader("User-Agent", userAgent).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) {
        try {
            Node body = new Node(html);
            String intro = body.text("#intro-block > div:eq(0)");
            String title = body.text("div.panel-heading > div");
            String cover = body.attr("img.lazy_img.img-responsive","src").trim();
            String author = body.text("#intro-block > div:eq(4) > span");
            String update = body.attr("#album_photo_cover > div:eq(1) > div:eq(3)","content");
            boolean status = isFinish(body.text("#intro-block > div:eq(2) > span"));
            comic.setInfo(title, cover, update, intro, author, status);
        }catch (Exception e){
            e.printStackTrace();
        }
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        int i=0;
        String startTitle = body.text(".col.btn.btn-primary.dropdown-toggle.reading").trim();
        String startPath = body.href(".col.btn.btn-primary.dropdown-toggle.reading");
        list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, startTitle, startPath));
        for (Node node : body.list("#episode-block > div > div.episode > ul > a")) {
            String title = node.text("li").trim();
            String path = node.href();
            list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path));
        }
        return Lists.reverse(list);
    }

    private String imgpath = "";
    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = baseUrl+path;
        imgpath = path;
        return new Request.Builder().url(url).addHeader("User-Agent", userAgent).addHeader("Href", baseUrl).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new ArrayList<>();
        try {
            int i = 0;
            for (Node node : new Node(html).list("img.lazy_img")) {
                Long comicChapter = chapter.getId();
                Long id = Long.parseLong(comicChapter + "000" + i);

                String img1 = node.attr("img","src");
                String img2 = node.attr("img","data-original");
                String reg[] = imgpath.split("\\/");
                if (img1.contains(reg[2])){
                    list.add(new ImageUrl(id, comicChapter, ++i, img1, false));
                }else if (img2.contains(reg[2])){
                    list.add(new ImageUrl(id, comicChapter, ++i, img2, false));
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Request getCategoryRequest(String format, int page) {
        String url = StringUtils.format(format, page);
        return new Request.Builder().url(url)
                .addHeader("User-Agent", userAgent)
                .addHeader("Href", url)
                .build();
    }

    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        Node body = new Node(html);
        for (Node node : body.list("div.thumb-overlay-albums")) {
            String cid = node.attr("a", "href");
            String title = node.attr("a > img", "title");
            String cover = node.attr("a > img", "src");
            String author = "佚名";

            if(title.contains("[") && title.contains("]")) {
                String temp = title.substring(title.indexOf("[") + 1);
                author = temp.substring(0, temp.indexOf("]") - 1);
            }
            list.add(new Comic(TYPE, cid, title, cover, null, author));
        }
        return list;
    }


    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        return new Node(html).attr("#album_photo_cover > div:eq(1) > div:eq(3)","content");
    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", baseUrl);
    }

    private static class Category extends MangaCategory {

        @Override
        public boolean isComposite() {
            return true;
        }

        @Override
        public String getFormat(String... args) {
            String path = args[CATEGORY_SUBJECT].concat(" ").trim();
            if (path.isEmpty()) {
                path = String.valueOf(0);
            } else {
                path = path.replaceAll("\\s+", "-");
            }

            String format = StringUtils.format("%s%s", JMTT.baseUrl, path);
            return format;
        }

        @Override
        public List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("最近更新", "albums?o=mr&page=%d"));
            return list;
        }
    }
}
