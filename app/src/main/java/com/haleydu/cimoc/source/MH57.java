package com.haleydu.cimoc.source;

import android.util.Pair;

import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.MangaCategory;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.NodeIterator;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.parser.UrlFilter;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.DecryptionUtils;
import com.haleydu.cimoc.utils.StringUtils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * Created by Hiroshi on 2016/10/3.
 */

public class MH57 extends MangaParser {

    public static final int TYPE = 8;
    public static final String DEFAULT_TITLE = "57漫画";
    public static String host = "http://www.wuqimh.net/";

    private static final String[] servers = {
            "http://images.lancaier.com"
    };

    public MH57(Source source) {
        init(source, new Category());
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) {
        String url = StringUtils.format("%ssearch/q_%s-p-%d",host, keyword, page);
        return new Request.Builder().url(url).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        Node body = new Node(html);
        for (Node node : body.list("div.book-result > div.pager-cont > span.pager > span.current")) {
            try {
                if (Integer.parseInt(node.text()) < page) {
                    return null;
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return new NodeIterator(body.list("li.cf")) {
            @Override
            protected Comic parse(Node node) {
                String cid = node.attr("div:nth-child(1) >a", "href").substring(1);
                String title = node.attr("div:nth-child(1) >a", "title");
                String cover = node.attr("div:nth-child(1) >a >img", "src");
                String update = node.text("div:nth-child(2) > dl >dd >span >span:nth-child(3)");
                String author = node.text("div:nth-child(2) >dl >dd:nth-child(4) >span >a");
                return new Comic(TYPE, cid, title, cover, update, author);
            }
        };
    }

    @Override
    public String getUrl(String cid) {
        return host.concat(cid);
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("m.wuqimh.net"));
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = host.concat(cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) {
        Node body = new Node(html);
        String title = body.text(".book-title >h1");
        String cover = body.src(".hcover > img");
        String update = body.text("li.status > span >span:nth-child(3)");
        String author = body.text("ul.detail-list.cf > li:nth-child(2) > span:nth-child(2) >a");
        String intro = body.text(".book-intro >div:nth-child(2) > p");
        boolean status = isFinish(body.text("li.status > span >span:nth-child(2)"));
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        int i=0;
        for (Node node : body.list("#chpater-list-1 > ul > li > a")) {
            String title = node.attr("title");
            String path = node.href().substring(1);
            list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path));
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/%s", host, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new LinkedList<>();
        String packed = StringUtils.match("eval(.*?)\\n", html, 1);
        if (packed != null) {
            String result = DecryptionUtils.evalDecrypt(packed);
            String jsonString = StringUtils.match("'fs':\\s*(\\[.*?\\])", result, 1);
            try {
                JSONArray array = new JSONArray(jsonString);
                int size = array.length();
                for (int i = 0; i != size; ++i) {
                    String url = array.getString(i);
                    if(url.indexOf("http://") == -1){
                        url = servers[0] + url;
                    }
                    Long comicChapter = chapter.getId();
                    Long id = Long.parseLong(comicChapter + "000" + i);
                    list.add(new ImageUrl(id, comicChapter, i + 1, url, false));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        return new Node(html).text("div.book-detail > div.cont-list > dl:eq(7) > dd");
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        Node body = new Node(html);
        for (Node node : body.list("span.pager > span.current")) {
            try {
                if (Integer.parseInt(node.text()) < page) {
                    return list;
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        for (Node node : body.list("#contList > li")) {
            String cid = node.hrefWithSplit("a", 0);
            String title = node.attr("a", "title");
            String cover = node.attr("a > img", "data-src");
            String update = node.textWithSubstring("span.updateon", 4, 14);
            list.add(new Comic(TYPE, cid, title, cover, update, null));
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
            return StringUtils.format("http://www.5qmh.com/list/area-%s-smid-%s-year-%s-lz-%s-order-%s-p-%%d",
                    args[CATEGORY_AREA], args[CATEGORY_SUBJECT], args[CATEGORY_YEAR], args[CATEGORY_PROGRESS], args[CATEGORY_ORDER]);
        }

        @Override
        public List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("热血", "1"));
            list.add(Pair.create("武侠", "2"));
            list.add(Pair.create("搞笑", "3"));
            list.add(Pair.create("耽美", "4"));
            list.add(Pair.create("爱情", "5"));
            list.add(Pair.create("科幻", "6"));
            list.add(Pair.create("魔法", "7"));
            list.add(Pair.create("神魔", "8"));
            list.add(Pair.create("竞技", "9"));
            list.add(Pair.create("格斗", "10"));
            list.add(Pair.create("机战", "11"));
            list.add(Pair.create("体育", "12"));
            list.add(Pair.create("运动", "13"));
            list.add(Pair.create("校园", "14"));
            list.add(Pair.create("励志", "15"));
            list.add(Pair.create("历史", "16"));
            list.add(Pair.create("伪娘", "17"));
            list.add(Pair.create("百合", "18"));
            list.add(Pair.create("后宫", "19"));
            list.add(Pair.create("治愈", "20"));
            list.add(Pair.create("美食", "21"));
            list.add(Pair.create("推理", "22"));
            list.add(Pair.create("悬疑", "23"));
            list.add(Pair.create("恐怖", "24"));
            list.add(Pair.create("职场", "25"));
            list.add(Pair.create("BL", "26"));
            list.add(Pair.create("剧情", "27"));
            list.add(Pair.create("生活", "28"));
            list.add(Pair.create("幻想", "29"));
            list.add(Pair.create("战争", "30"));
            list.add(Pair.create("仙侠", "33"));
            list.add(Pair.create("性转换", "40"));
            list.add(Pair.create("冒险", "41"));
            list.add(Pair.create("其他", "32"));
            return list;
        }

        @Override
        public boolean hasArea() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getArea() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("日本", "日本"));
            list.add(Pair.create("港台", "港台"));
            list.add(Pair.create("欧美", "欧美"));
            list.add(Pair.create("韩国", "韩国"));
            list.add(Pair.create("国产", "国产"));
            list.add(Pair.create("其它", "其它"));
            return list;
        }

        @Override
        public boolean hasYear() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getYear() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("2017", "2017"));
            list.add(Pair.create("2016", "2016"));
            list.add(Pair.create("2015", "2015"));
            list.add(Pair.create("2014", "2014"));
            list.add(Pair.create("2013", "2013"));
            list.add(Pair.create("2012", "2012"));
            list.add(Pair.create("2011", "2011"));
            list.add(Pair.create("2010", "2010"));
            list.add(Pair.create("2009", "2009"));
            list.add(Pair.create("2008", "2008"));
            list.add(Pair.create("2007", "2007"));
            list.add(Pair.create("2006", "2006"));
            list.add(Pair.create("2005", "2005"));
            list.add(Pair.create("2004", "2004"));
            list.add(Pair.create("2003", "2003"));
            list.add(Pair.create("2002", "2002"));
            list.add(Pair.create("2001", "2001"));
            list.add(Pair.create("2000", "2000"));
            list.add(Pair.create("1990", "1990"));
            return list;
        }

        @Override
        public boolean hasProgress() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("连载", "1"));
            list.add(Pair.create("完结", "2"));
            return list;
        }

        @Override
        protected boolean hasOrder() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("更新", "addtime"));
            list.add(Pair.create("发布", "id"));
            list.add(Pair.create("人气", "hits"));
            list.add(Pair.create("评分", "gold"));
            return list;
        }

    }

}
