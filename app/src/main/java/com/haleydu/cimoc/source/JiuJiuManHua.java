package com.haleydu.cimoc.source;

import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.haleydu.cimoc.core.Manga;
import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.NodeIterator;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.DecryptionUtils;
import com.haleydu.cimoc.utils.StringUtils;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Request;

public class JiuJiuManHua extends MangaParser {
    public static final int TYPE = 148;
    public static final String DEFAULT_TITLE = "久久漫画";
    public static final String baseUrl = "http://99hanman.top";
    final  String TAG = "JiuJiuManHua";

    public  JiuJiuManHua(Source source) {
        init(source, null);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException, Exception {
        if (page != 1) return null;
        String url = StringUtils.format(baseUrl + "/search?keyword=%s", keyword);
        return new Request.Builder().url(url).build();
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {

        Node body = new Node(html);
        return new NodeIterator(body.list(".mh-item")) {
            @Override
            protected Comic parse(Node node) {
                String href = node.attr("a", "href");
                String cid = href;
                String title = node.attr("a", "title");
                String temp = node.attr("a>p", "style");
                temp = temp.substring(temp.indexOf("(") + 1);
                temp = temp.substring(0, temp.lastIndexOf(")"));
                String cover = temp;
                return new Comic(TYPE, cid, title, cover, null, null);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = baseUrl.concat(cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        String title = body.text(".info> h1");
        String cover = body.src(".banner_detail_form >div > img");
        String author = body.text(".subtitle:nth-child(3)").trim().replace("作者：", "");
        String intro = body.text(".content");
        boolean status = isFinish(body.text(".tip > span > span"));

        String update = body.text(".tip > span:nth-child(3)");
        if (update == null || update.equals("")) {
            update = body.text("a.comic-pub-date");
        }
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        int i=0;
        for (Node node : new Node(html).list("#detail-list-select > li")) {
            String title = node.text();
            String path = node.attr("a", "href");
            list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path));
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {

        String url = StringUtils.format("%s%s.html", baseUrl, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new ArrayList<>();

        try {

            Node node = new Node(html);
            long id = 0;
            for (Node childNode : node.list(".comicpage > div >img"))
            {
                String imageUrl = childNode.attr("data-original");
                list.add(new ImageUrl(id, chapter.getId(), (int)id, imageUrl, false));
                id++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
