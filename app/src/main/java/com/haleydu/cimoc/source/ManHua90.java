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
import com.haleydu.cimoc.model.Tag;
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
//90漫画源
public class ManHua90 extends MangaParser {
    public static final int TYPE = 147;
    public static final String DEFAULT_TITLE = "90漫画";
    private final String host = "http://wap.90mh.com/";

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }
    //搜索页面得到结果
    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException, Exception {
        Log.d("MANHUA90", "调用getSearchRequest");
        String url = "";
        if (page == 1) {
            url = StringUtils.format("http://wap.90mh.com/search/?keywords=%s", keyword);
        }
        return new Request.Builder().url(url).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        return new NodeIterator(body.list(".itemImg > a")) {
            @Override
            protected Comic parse(Node node) {
                String cid = node.href().replace(host, "");
                String title = node.attr("mip-img", "alt");
                String cover = node.attr("mip-img", "src");
                return new Comic(TYPE, cid, title, cover, null, null);
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
        String title = body.text("body > div.comic-view.clearfix > div.view-sub.autoHeight > h1");
        String cover = body.src("#Cover > mip-img");
        String author = body.text("body > div.comic-view.clearfix > div.view-sub.autoHeight > div > dl:nth-child(6) > dd > a");
        String intro = body.text(".txtDesc.autoHeight");
        boolean status = isFinish(body.text("body > div.comic-view.clearfix > div.view-sub.autoHeight > div > dl:nth-child(4) > dd"));

        String update = body.text("body > div.comic-view.clearfix > div.view-sub.autoHeight > div > dl:nth-child(9) > dd");
        if (update == null || update.equals("")) {
            update = "没找到最后更新日期";
        }
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        int i=0;
        for (Node node : new Node(html).list("div.comic-chapters >div > ul > li >a")) {
            String title = node.text("span");
            String path = node.hrefWithSplit(2);
            list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path));
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s%s%s.html", host, cid, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new ArrayList<>();

        try {

            Node node = new Node(html);

            //Request request = new Request.Builder().url("").build();
            //Manga::getResponseBody()

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
