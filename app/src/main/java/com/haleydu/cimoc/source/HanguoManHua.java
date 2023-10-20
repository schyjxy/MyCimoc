package com.haleydu.cimoc.source;

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

import org.json.JSONException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;

public class HanguoManHua extends MangaParser {
    public static final int TYPE = 101;
    public static final String DEFAULT_TITLE = "韩国漫画";
    public static final String host = "https://www.mxshm.site/";

    public HanguoManHua(Source source)
    {
        init(source, new HanguoManHua.Category());
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException, Exception {
        String url = "";
        url = StringUtils.format("%s/search?keyword=%s", host, keyword);
        return new Request.Builder().url(url).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        return new NodeIterator(body.list(".mh-item")) {
            @Override
            protected Comic parse(Node node) {
                String cid = node.href("a");
                String title = node.attr("a", "title");
                String cover =  node.attr("a > p", "style");
                cover = cover.substring(cover.indexOf("(") + 1);
                cover = cover.substring(0, cover.lastIndexOf(")")) ;
                String update = node.text("div > p:nth-child(3) >a") ;
                return new Comic(TYPE, cid, title, cover, update, null);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = host.concat(cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        String title = body.getChild(".info > h1").text();
        String cover = body.src(".banner_detail_form > .cover > img");
        String author = body.text(".info > p:nth-child(4)");
        String intro = body.text(".content > span");
        boolean status = isFinish(body.text(".info > .tip > span:nth-child(1) > span"));
        String update = body.text(".info > .tip > span:nth-child(3)");
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        int i=0;
        for (Node node : new Node(html).list("#chapterlistload > ul >li >a")) {
            String title = node.text();
            String path = node.href();
            list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path));
        }

        Collections.reverse(list);
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s%s", host, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new LinkedList<ImageUrl>();
        long id = 0;
        for (Node node : new Node(html).list(".comicpage > div > img")) {
            String url = node.attr("data-original");
            list.add(new ImageUrl(id++, chapter.getId(), (int)id, url, false));
        }

        return list;
    }

    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        Node body = new Node(html);
        for (Node node : body.list(".mh-item")) {

            String cid = node.href("a");
            String title = node.attr("a", "title");
            String cover =  node.attr("a > p", "style");
            cover = cover.substring(cover.indexOf("(") + 1);
            cover = cover.substring(0, cover.lastIndexOf(")")) ;
            String update = node.text("div > p:nth-child(3) >a") ;

            list.add(new Comic(TYPE, cid, title, cover, update, null));
        }
        return list;
    }

    @Override
    public Request getCategoryRequest(String format, int page) {
        String url = StringUtils.format(format, page);
        return new Request.Builder().url(url)
                //.addHeader("User-Agent", userAgent)
                .addHeader("Href", url)
                .build();
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

            String format = StringUtils.format("%s%s", HanguoManHua.host, path);
            return format;
        }

        @Override
        public List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("连载", "booklist?end=0&page=%d"));
            list.add(Pair.create("完结", "booklist?end=1&page=%d"));
            return list;
        }
    }
}
