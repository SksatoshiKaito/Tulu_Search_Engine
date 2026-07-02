package org.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {
    private static ElasticsearchClient esClient;
    private static boolean esConnected = false;

    public static void main(String[] args) throws Exception {
        try {
            String esHost = System.getenv("ES_HOST") != null ? System.getenv("ES_HOST") : "localhost";
            int esPort = System.getenv("ES_PORT") != null ? Integer.parseInt(System.getenv("ES_PORT")) : 9200;
            RestClient restClient = RestClient.builder(
                new HttpHost(esHost, esPort)
            ).setRequestConfigCallback(b -> b
                .setConnectTimeout(3000)
                .setSocketTimeout(5000)
            ).build();
            esClient = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper())
            );
            esClient.info();
            esConnected = true;
            System.out.println("✅ Elasticsearch connected.");
        } catch (Exception e) {
            System.out.println("⚠  Elasticsearch not available yet. Server will still start.");
        }

        int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8082;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new SearchHandler());
        server.createContext("/suggest", new SuggestHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("✅ Tulu Search Engine Live → Port " + port);
    }

    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String rawQuery = ex.getRequestURI().getRawQuery();
            String q = "";
            int start = 0;
            String tbm = "";
            if (rawQuery != null) {
                for (String p : rawQuery.split("&")) {
                    if (p.startsWith("q=")) q = URLDecoder.decode(p.substring(2), StandardCharsets.UTF_8);
                    else if (p.startsWith("start=")) { try { start = Integer.parseInt(p.substring(6)); } catch (Exception ignored) {} }
                    else if (p.startsWith("tbm=")) tbm = URLDecoder.decode(p.substring(4), StandardCharsets.UTF_8);
                }
            }
            String html = q.isEmpty() ? buildHome() : buildResults(q, start, tbm);
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    static class SuggestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String rawQuery = ex.getRequestURI().getRawQuery();
            String q = "";
            if (rawQuery != null) {
                for (String p : rawQuery.split("&")) {
                    if (p.startsWith("q=")) q = URLDecoder.decode(p.substring(2), StandardCharsets.UTF_8).toLowerCase();
                }
            }
            StringBuilder json = new StringBuilder("[");
            if (!q.isEmpty() && esConnected) {
                try {
                    final String qF = q;
                    var res = esClient.search(s -> s
                        .index("search_index")
                        .size(6)
                        .query(qu -> qu.matchPhrasePrefix(mp -> mp.field("title").query(qF))),
                        Map.class);
                    boolean first = true;
                    Set<String> seen = new HashSet<>();
                    for (Hit<Map> hit : res.hits().hits()) {
                        String ti = (String) hit.source().get("title");
                        if (ti == null || ti.length() > 60 || !seen.add(ti.toLowerCase())) continue;
                        if (!first) json.append(",");
                        json.append("\"").append(ti.replace("\"", "\\\"").replace("\n", " ")).append("\"");
                        first = false;
                    }
                } catch (Exception e) {}
            }
            json.append("]");
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    // ─── HOME PAGE ──────────────────────────────────────────────────────────────
    private static String buildHome() {
        return "<!DOCTYPE html><html lang='en'><head>" +
            "<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>Tulu – Cognitive Search</title>" +
            "<link href='https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap' rel='stylesheet'>" +
            "<style>" + baseCss() + homeCss() + "</style></head><body>" +
            "<div class='home-wrap'>" +
              "<div class='neural-bg'></div>" +
              "<div class='home-center'>" +
                "<div class='logo-wrap'>" +
                  "<div class='logo-mark'>" +
                    "<svg width='90' height='90' viewBox='0 0 110 100' fill='none' xmlns='http://www.w3.org/2000/svg'>" +
                      "<defs>" +
                        "<linearGradient id='hBg' x1='0' y1='0' x2='0' y2='1'><stop offset='0%' stop-color='#161821'/><stop offset='100%' stop-color='#0f1115'/></linearGradient>" +
                        "<linearGradient id='swsh' x1='0' y1='0' x2='1' y2='0'><stop offset='0%' stop-color='#60a5fa'/><stop offset='100%' stop-color='#3b82f6'/></linearGradient>" +
                      "</defs>" +
                      "<!-- hexagon body -->" +
                      "<polygon points='60,3 102,25 102,72 60,96 18,72 18,25' fill='url(#hBg)' stroke='#313745' stroke-width='1.5'/>" +
                      "<!-- gradient swoosh sweep at bottom -->" +
                      "<path d='M23,68 Q60,110 97,68 Q83,96 60,96 Q37,96 23,68Z' fill='url(#swsh)'/>" +
                      "<!-- white T horizontal bar -->" +
                      "<rect x='38' y='29' width='44' height='13' rx='3' fill='#cbd5e1'/>" +
                      "<!-- white T vertical bar -->" +
                      "<rect x='53' y='42' width='14' height='28' rx='3' fill='#cbd5e1'/>" +
                      "<!-- circuit line dots on left -->" +
                      "<circle cx='4' cy='43' r='3.5' fill='#93c5fd'/><line x1='7' y1='43' x2='22' y2='43' stroke='#93c5fd' stroke-width='2.5' stroke-linecap='round'/>" +
                      "<circle cx='4' cy='54' r='3.5' fill='#60a5fa'/><line x1='7' y1='54' x2='22' y2='54' stroke='#60a5fa' stroke-width='2.5' stroke-linecap='round'/>" +
                      "<circle cx='4' cy='65' r='3.5' fill='#3b82f6'/><line x1='7' y1='65' x2='22' y2='65' stroke='#3b82f6' stroke-width='2.5' stroke-linecap='round'/>" +
                    "</svg>" +
                  "</div>" +
                  "<h1 class='logo-text'>TUL<span class='logo-u'>U</span></h1>" +
                  "<p class='logo-sub'>Cognitive Search Engine</p>" +
                "</div>" +
                "<form action='/' method='GET' class='home-form' id='sf'>" +
                  "<div class='search-shell' id='sb-container'>" +
                    "<span class='sh-icon'>" + svgSearch() + "</span>" +
                    "<input class='sh-input' id='si' name='q' placeholder='Search anything...' autocomplete='off'>" +
                    "<button type='button' class='sh-btn-voice' onclick='startVoiceSearch()' title='Voice search'>" + svgMic() + "</button>" +
                    "<button type='submit' class='sh-btn-go'>" + svgSearchB() + "</button>" +
                  "</div>" +
                  "<div id='history-dropdown' class='hist-drop' style='display:none'></div>" +
                "</form>" +
                "<div class='home-slogan'>Understand deeper. Discover smarter.</div>" +
              "</div>" +
            "</div>" +
            jsCode() + "</body></html>";
    }

    // ─── RESULTS PAGE ────────────────────────────────────────────────────────────
    private static String buildResults(String q, int start, String tbm) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='en'><head>")
          .append("<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>").append(esc(q)).append(" — Tulu</title>")
          .append("<link href='https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap' rel='stylesheet'>")
          .append("<style>").append(baseCss()).append(resultsCss()).append("</style>")
          .append("</head><body>");

        // ── HEADER ──
        sb.append("<header class='r-header'>")
          .append("<a href='/' class='r-logo'>")
          .append("<div class='r-logo-box'>")
              .append("<svg width='34' height='34' viewBox='0 0 110 100' fill='none' xmlns='http://www.w3.org/2000/svg'>")
                .append("<defs>")
                  .append("<linearGradient id='hBg2' x1='0' y1='0' x2='0' y2='1'><stop offset='0%' stop-color='#161821'/><stop offset='100%' stop-color='#0f1115'/></linearGradient>")
                  .append("<linearGradient id='swsh2' x1='0' y1='0' x2='1' y2='0'><stop offset='0%' stop-color='#60a5fa'/><stop offset='100%' stop-color='#3b82f6'/></linearGradient>")
                .append("</defs>")
                .append("<polygon points='60,3 102,25 102,72 60,96 18,72 18,25' fill='url(#hBg2)' stroke='#313745' stroke-width='1.5'/>")
                .append("<path d='M23,68 Q60,110 97,68 Q83,96 60,96 Q37,96 23,68Z' fill='url(#swsh2)'/>")
                .append("<rect x='38' y='29' width='44' height='13' rx='3' fill='#cbd5e1'/>")
                .append("<rect x='53' y='42' width='14' height='28' rx='3' fill='#cbd5e1'/>")
                .append("<circle cx='4' cy='43' r='3.5' fill='#93c5fd'/><line x1='7' y1='43' x2='22' y2='43' stroke='#93c5fd' stroke-width='2.5' stroke-linecap='round'/>")
                .append("<circle cx='4' cy='54' r='3.5' fill='#60a5fa'/><line x1='7' y1='54' x2='22' y2='54' stroke='#60a5fa' stroke-width='2.5' stroke-linecap='round'/>")
                .append("<circle cx='4' cy='65' r='3.5' fill='#3b82f6'/><line x1='7' y1='65' x2='22' y2='65' stroke='#3b82f6' stroke-width='2.5' stroke-linecap='round'/>")
              .append("</svg>")
            .append("</div>")
            .append("<span class='r-logo-name'>TULU</span>")
          .append("</a>")
          .append("<div class='r-bar-wrap'>")
          .append("<form action='/' method='GET' id='sf'>")
          .append("<div class='r-search-shell' id='sb-container'>")
          .append("<input type='hidden' name='tbm' value='").append(esc(tbm)).append("'>")
          .append("<span class='rsh-icon'>").append(svgSearch()).append("</span>")
          .append("<input class='rsh-input' id='si' name='q' value='").append(esc(q)).append("' autocomplete='off'>")
          .append("<button type='button' class='rsh-clear' onclick='document.getElementById(\"si\").value=\"\";document.getElementById(\"si\").focus();'>").append(svgX()).append("</button>")
          .append("<div class='rsh-div'></div>")
          .append("<button type='button' class='rsh-voice' onclick='startVoiceSearch()'>").append(svgMic()).append("</button>")
          .append("<button type='submit' class='rsh-go'>").append(svgSearch()).append("</button>")
          .append("</div>")
          .append("<div id='history-dropdown' class='hist-drop-res' style='display:none'></div>")
          .append("</form></div>")
          .append("</header>");

        // ── TABS ──
        String aAll = tbm.isEmpty() ? "rt-active" : "";
        String aImg = "isch".equals(tbm) ? "rt-active" : "";
        String aNws = "nws".equals(tbm) ? "rt-active" : "";

        sb.append("<div class='r-tabs'>")
          .append("<div class='r-tabs-inner'>")
          .append(tab("/?q="+escUrl(q), "All", aAll))
          .append(tab("/?q="+escUrl(q)+"&tbm=isch", "Images", aImg))
          .append(tab("/?q="+escUrl(q)+"&tbm=nws", "News", aNws))
          .append("</div></div>");

        // ── CONTENT ──
        boolean isImg = "isch".equals(tbm);
        sb.append("<div class='r-body'>")
          .append("<div class='r-main").append(isImg ? " r-main-full" : "").append("'>");

        if (!esConnected) tryReconnect();

        if (!esConnected) {
            sb.append("<div class='err-box'><div class='err-icon'>⚡</div><h2>Connection Lost</h2><p>Cannot reach Elasticsearch at <code>localhost:9200</code>. Please ensure it is running.</p></div>");
        } else {
            try {
                final String qF = q;
                int size = isImg ? 40 : 10;
                var res = esClient.search(s -> s
                    .index("search_index")
                    .from(start).size(size).minScore(0.5)
                    .query(qu -> qu.bool(b -> {
                        b.must(mu -> mu.bool(ib -> ib
                            .should(sh -> sh.matchPhrase(mp -> mp.field("title").query(qF).boost(20f)))
                            .should(sh -> sh.matchPhrase(mp -> mp.field("content").query(qF).boost(5f)))
                            .should(sh -> sh.matchPhrase(mp -> mp.field("url").query(qF).boost(8f)))
                            .should(sh -> sh.multiMatch(mm -> mm.fields("title^10","url^8","content^1").query(qF)
                                .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)))
                            .minimumShouldMatch("1")
                        ));
                        b.should(sh -> sh.queryString(qs -> qs.defaultField("domain").query("*wikipedia* OR *github* OR *w3schools* OR *stackoverflow* OR *aust*").boost(15f)));
                        b.should(sh -> sh.range(r -> r.field("url_depth").lte(co.elastic.clients.json.JsonData.of(2)).boost(10f)));
                        if ("nws".equals(tbm)) {
                            b.filter(f -> f.queryString(qs -> qs.defaultField("domain").query("*news* OR *bbc* OR *cnn* OR *reuters* OR *prothomalo* OR *dailystar* OR *bdnews* OR *dhakatribune* OR *tbs* OR *daily* OR *times* OR *post* OR *tribune* OR *herald* OR *express* OR *guardian* OR *journal* OR *media* OR *press* OR *report* OR *wire* OR *today* OR *star*")));
                        } else if ("isch".equals(tbm)) {
                            b.filter(f -> f.exists(e -> e.field("images")));
                        }
                        return b;
                    }))
                    .collapse(c -> c.field("domain"))
                    .highlight(h -> h
                        .fields("title", hf -> hf.preTags("").postTags("").numberOfFragments(0))
                        .fields("content", hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(240).numberOfFragments(2))),
                    Map.class);

                List<Hit<Map>> hits = res.hits().hits();
                long total = res.hits().total() != null ? res.hits().total().value() : hits.size();

                if (hits.isEmpty()) {
                    sb.append(noResults(q));
                } else {
                    if (isImg) {
                        sb.append("<div class='img-grid'>");
                        for (Hit<Map> hit : hits) {
                            List<String> imgs = (List<String>) hit.source().get("images");
                            if (imgs != null) {
                                for (String imgUrl : imgs) {
                                    String dm = safe(hit.source(), "domain");
                                    String url2 = safe(hit.source(), "url");
                                    String ti = safe(hit.source(), "title");
                                    String fav = "https://www.google.com/s2/favicons?domain=" + dm + "&sz=32";
                                    sb.append("<div class='ig-card'>")
                                      .append("<a href='").append(url2).append("'>")
                                      .append("<img src='").append(esc(imgUrl)).append("' class='ig-img' loading='lazy' onerror='this.closest(\".ig-card\").style.display=\"none\"'>")
                                      .append("</a>")
                                      .append("<div class='ig-meta'>")
                                      .append("<img src='").append(fav).append("' class='ig-fav' onerror='this.style.display=\"none\"'>")
                                      .append("<div class='ig-info'>")
                                      .append("<div class='ig-title'>").append(esc(ti)).append("</div>")
                                      .append("<div class='ig-domain'>").append(esc(dm)).append("</div>")
                                      .append("</div></div></div>");
                                }
                            }
                        }
                        sb.append("</div>");
                    } else if ("nws".equals(tbm)) {
                        sb.append("<div class='r-count'>").append(total).append(" results found</div>");
                        for (Hit<Map> hit : hits) sb.append(renderNews(hit));
                    } else {
                        sb.append("<div class='r-count'>").append(total).append(" results found</div>");
                        for (Hit<Map> hit : hits) sb.append(renderResult(hit));
                    }
                    if (total > size) sb.append(renderPagination(q, tbm, start, total, size));
                }
            } catch (Exception e) {
                esConnected = false;
                sb.append("<div class='err-box'><div class='err-icon'>⚠</div><h2>Query Error</h2><p><code>").append(esc(e.toString())).append("</code></p></div>");
            }
        }

        sb.append("</div>"); // r-main
        if (tbm.isEmpty()) sb.append(knowledgePanel(q));
        sb.append("</div>"); // r-body
        sb.append(jsCode()).append("</body></html>");
        return sb.toString();
    }

    // ─── RENDER HELPERS ──────────────────────────────────────────────────────────
    private static String tab(String href, String label, String active) {
        return "<a href='" + href + "' class='r-tab " + active + "'>" + label + "</a>";
    }

    private static String renderResult(Hit<Map> hit) {
        Map<?,?> src = hit.source();
        String url = safe(src,"url"), title = safe(src,"title"), domain = safe(src,"domain");
        String snippet;
        if (hit.highlight() != null && hit.highlight().containsKey("content"))
            snippet = String.join(" … ", hit.highlight().get("content"));
        else { String r = safe(src,"content"); snippet = r.length()>240 ? r.substring(0,240)+"…" : r; }
        String dispTitle = (hit.highlight() != null && hit.highlight().containsKey("title") && !hit.highlight().get("title").isEmpty())
            ? hit.highlight().get("title").get(0) : title;
        if (dispTitle.isBlank()) dispTitle = domain;
        String bread = url.replaceFirst("https?://",""); if (bread.length()>70) bread=bread.substring(0,67)+"…";
        String fav = "https://www.google.com/s2/favicons?domain="+domain+"&sz=32";
        return "<div class='rc'>" +
               "<div class='rc-head'><img src='"+fav+"' class='rc-fav' onerror='this.style.display=\"none\"'>" +
               "<div><div class='rc-domain'>"+esc(domain)+"</div><div class='rc-bread'>"+esc(bread)+"</div></div></div>" +
               "<a href='"+url+"'><h2 class='rc-title'>"+dispTitle+"</h2></a>" +
               "<p class='rc-snip'>"+snippet+"</p></div>";
    }

    private static String renderNews(Hit<Map> hit) {
        Map<?,?> src = hit.source();
        String url=safe(src,"url"), title=safe(src,"title"), domain=safe(src,"domain");
        String img = firstImage(src);
        String snippet;
        if (hit.highlight() != null && hit.highlight().containsKey("content"))
            snippet = String.join(" … ", hit.highlight().get("content"));
        else { String r=safe(src,"content"); snippet=r.length()>160?r.substring(0,160)+"…":r; }
        int hrs = (Math.abs(url.hashCode())%23)+1;
        String fav = "https://www.google.com/s2/favicons?domain="+domain+"&sz=32";
        return "<div class='nws'>" +
               "<div class='nws-l'>" +
               "<div class='nws-src'><img src='"+fav+"' class='nws-fav' onerror='this.style.display=\"none\"'><span>"+esc(domain)+"</span></div>" +
               "<a href='"+url+"'><h2 class='nws-title'>"+esc(title)+"</h2></a>" +
               "<p class='nws-snip'>"+snippet+"</p>" +
               "<span class='nws-time'>"+hrs+" hours ago</span>" +
               "</div>" +
               "<img src='"+esc(img)+"' class='nws-img' onerror='this.style.display=\"none\"'>" +
               "</div>";
    }

    private static String renderPagination(String q, String tbm, int start, long total, int size) {
        StringBuilder pb = new StringBuilder();
        int pages = Math.min(6,(int)Math.ceil((double)total/size));
        int cur = (start/size)+1;
        pb.append("<div class='pag'>");
        if (cur > 1) pb.append("<a href='/?q=").append(escUrl(q)).append("&tbm=").append(tbm).append("&start=").append((cur-2)*size).append("' class='pag-btn'>← Prev</a>");
        for (int i=1;i<=pages;i++) {
            int ps=(i-1)*size;
            if (i==cur) pb.append("<span class='pag-n pag-cur'>").append(i).append("</span>");
            else pb.append("<a href='/?q=").append(escUrl(q)).append("&tbm=").append(tbm).append("&start=").append(ps).append("' class='pag-n'>").append(i).append("</a>");
        }
        if (cur<pages) pb.append("<a href='/?q=").append(escUrl(q)).append("&tbm=").append(tbm).append("&start=").append(cur*size).append("' class='pag-btn'>Next →</a>");
        pb.append("</div>");
        return pb.toString();
    }

    private static String knowledgePanel(String q) {
        String ql = q.toLowerCase();
        Object[][] panels = {
            {"youtube","YouTube","Video Sharing Platform","YouTube is a global video sharing platform owned by Google LLC. Launched in 2005.",new String[]{"Founded","2005","CEO","Neal Mohan","Parent","Google LLC"},"youtube.com"},
            {"aust","AUST","Ahsanullah University of Science & Technology","A premier private engineering university in Dhaka, Bangladesh established by Dhaka Ahsania Mission in 1995.",new String[]{"Founded","1995","Type","Private University","Location","Dhaka, Bangladesh"},"aust.edu"},
            {"github","GitHub","Software Development & Version Control","GitHub is the world's leading software collaboration platform, acquired by Microsoft in 2018.",new String[]{"Founded","2008","CEO","Thomas Dohmke","Owner","Microsoft"},"github.com"},
            {"facebook","Facebook","Social Media Platform","Facebook is a social network owned by Meta Platforms Inc., founded by Mark Zuckerberg in 2004.",new String[]{"Founded","2004","CEO","Mark Zuckerberg","Parent","Meta Platforms"},"facebook.com"},
            {"google","Google","Search Engine & Technology Company","Google LLC is a multinational technology company specializing in internet-related services and products.",new String[]{"Founded","1998","CEO","Sundar Pichai","HQ","Mountain View, CA"},"google.com"}
        };
        for (Object[] p : panels) {
            if (ql.contains((String)p[0])) {
                String name=(String)p[1], sub=(String)p[2], desc=(String)p[3], dom=(String)p[5];
                String[] facts=(String[])p[4];
                StringBuilder kp = new StringBuilder();
                kp.append("<div class='kp'><div class='kp-top'><img class='kp-ico' src='https://www.google.com/s2/favicons?domain=").append(dom).append("&sz=64' onerror='this.style.display=\"none\"'>")
                  .append("<div><div class='kp-name'>").append(esc(name)).append("</div><div class='kp-sub'>").append(esc(sub)).append("</div></div></div>")
                  .append("<p class='kp-desc'>").append(esc(desc)).append("</p><div class='kp-facts'>");
                for (int i=0;i<facts.length-1;i+=2)
                    kp.append("<div class='kp-row'><span class='kp-key'>").append(esc(facts[i])).append("</span><span class='kp-val'>").append(esc(facts[i+1])).append("</span></div>");
                kp.append("</div></div>");
                return kp.toString();
            }
        }
        return "";
    }

    private static String noResults(String q) {
        return "<div class='no-res'><div class='nr-icon'>◎</div><h2>No results for <em>\""+esc(q)+"\"</em></h2><p>Try using different keywords or check spelling.</p></div>";
    }
    private static String noResultsType(String q, String type) {
        return "<div class='no-res'><div class='nr-icon'>◎</div><h2>No "+type+" found for <em>\""+esc(q)+"\"</em></h2><p>The indexed pages do not contain any "+type+" matching this query.</p></div>";
    }

    // ─── DOMAIN FILTERS ──────────────────────────────────────────────────────────
    private static boolean isVideoDomain(String d) {
        d = d.toLowerCase();
        return d.contains("youtube") || d.contains("youtu.be") || d.contains("vimeo") ||
               d.contains("dailymotion") || d.contains("twitch") || d.contains("tiktok") ||
               d.contains("rumble") || d.contains("bilibili") || d.contains("odysee") ||
               d.contains("video") || d.contains("watch") || d.contains("stream");
    }
    private static boolean isNewsDomain(String d) {
        d = d.toLowerCase();
        return d.contains("news") || d.contains("bbc") || d.contains("cnn") || d.contains("reuters") ||
               d.contains("prothomalo") || d.contains("dailystar") || d.contains("bdnews") ||
               d.contains("dhakatribune") || d.contains("tbs") || d.contains("daily") ||
               d.contains("times") || d.contains("post") || d.contains("tribune") ||
               d.contains("herald") || d.contains("express") || d.contains("guardian") ||
               d.contains("journal") || d.contains("media") || d.contains("press") ||
               d.contains("report") || d.contains("wire") || d.contains("today") || d.contains("star");
    }

    // ─── CSS ─────────────────────────────────────────────────────────────────────
    private static String baseCss() {
        return "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap');" +
               "*{box-sizing:border-box;margin:0;padding:0}" +
               "body{font-family:'Inter',system-ui,sans-serif;background:#0f1115;color:#cbd5e1;min-height:100vh;overflow-x:hidden}" +
               "a{text-decoration:none;color:inherit}" +
               "em{font-style:normal;font-weight:600;color:#60a5fa}" +
               "code{font-family:monospace;background:rgba(96,165,250,0.1);color:#60a5fa;padding:2px 8px;border-radius:4px;font-size:12px}" +
               // Search shell
               ".search-shell,.r-search-shell{display:flex;align-items:center;background:#1a1d24;border:1px solid #313745;border-radius:24px;transition:box-shadow 0.2s,border-color 0.2s;position:relative;z-index:101}" +
               ".search-shell:hover,.r-search-shell:hover{box-shadow:0 1px 12px rgba(59,130,246,0.15);border-color:#3b82f6}" +
               ".search-shell:focus-within,.r-search-shell:focus-within{box-shadow:0 1px 12px rgba(59,130,246,0.25);border-color:#60a5fa}" +
               ".search-shell.hist-open,.r-search-shell.hist-open{border-bottom-left-radius:0;border-bottom-right-radius:0;border-bottom-color:transparent;box-shadow:0 1px 12px rgba(59,130,246,0.15)}" +
               // History dropdown
               ".hist-drop,.hist-drop-res{position:absolute;top:100%;left:-1px;right:-1px;background:#1a1d24;border:1px solid transparent;border-top:1px solid #313745;border-bottom-left-radius:24px;border-bottom-right-radius:24px;box-shadow:0 8px 16px rgba(0,0,0,0.5);z-index:100;overflow:hidden}" +
               ".hist-row{display:flex;align-items:center;padding:8px 20px;gap:12px;cursor:pointer;transition:background 0.1s}" +
               ".hist-row:hover{background:rgba(96,165,250,0.1)}" +
               ".hist-clk{fill:#64748b;width:16px;height:16px;flex-shrink:0}" +
               ".hist-txt{color:#e2e8f0;font-size:15px;flex:1;font-weight:500}" +
               ".hist-rm{color:#64748b;font-size:14px;line-height:1;padding:4px 8px;transition:color 0.15s;cursor:pointer}" +
               ".hist-rm:hover{color:#f87171;text-decoration:underline}" +
               // Error/no results
               ".err-box{background:rgba(239,68,68,0.05);border:1px solid rgba(239,68,68,0.2);border-radius:12px;padding:40px;text-align:center;margin-top:40px}" +
               ".err-box .err-icon{font-size:36px;margin-bottom:16px;color:#f87171}" +
               ".err-box h2{font-size:20px;color:#f87171;margin-bottom:8px;font-weight:600}" +
               ".err-box p{color:#94a3b8;font-size:14px}" +
               ".no-res{text-align:center;padding:60px 0}" +
               ".nr-icon{font-size:44px;margin-bottom:20px;color:#334155}" +
               ".no-res h2{font-size:20px;font-weight:500;color:#e2e8f0;margin-bottom:8px}" +
               ".no-res em{color:#60a5fa;font-style:normal}" +
               ".no-res p{color:#94a3b8;font-size:14px}";
    }

    private static String homeCss() {
        return ".home-wrap{min-height:100vh;display:flex;align-items:center;justify-content:center;position:relative;background:#0f1115}" +
               ".home-center{position:relative;z-index:1;display:flex;flex-direction:column;align-items:center;gap:32px;width:100%;max-width:680px;padding:20px}" +
               // Logo
               ".logo-wrap{display:flex;flex-direction:column;align-items:center;gap:8px}" +
               ".logo-mark{filter:drop-shadow(0 2px 18px rgba(59,130,246,0.3))}" +
               ".logo-text{font-size:48px;font-weight:700;letter-spacing:4px;color:#e2e8f0;text-transform:uppercase;margin-right:-4px;display:flex}" +
               ".logo-u{color:#3b82f6}" +
               ".logo-sub{font-size:11px;letter-spacing:1.5px;text-transform:uppercase;color:#94a3b8;font-weight:500;display:flex;align-items:center;gap:8px}" +
               ".logo-sub::before,.logo-sub::after{content:'';display:block;width:15px;height:1px;background:#313745}" +
               // Search bar
               ".home-form{width:100%;max-width:584px;position:relative}" +
               ".search-shell{padding:0 8px 0 14px;height:46px;border-radius:24px}" +
               ".sh-icon{display:flex;flex-shrink:0;color:#64748b;padding-right:10px}" +
               ".sh-input{background:transparent;border:none;outline:none;color:#e2e8f0;font-size:16px;font-family:inherit;flex:1;padding:0;min-width:0}" +
               ".sh-input::placeholder{color:#64748b}" +
               ".sh-btn-voice{background:transparent;border:none;cursor:pointer;padding:8px;display:flex;align-items:center;border-radius:50%;color:#60a5fa;margin-right:2px;transition:background 0.2s}" +
               ".sh-btn-voice:hover{background:rgba(59,130,246,0.15)}" +
               ".sh-btn-go{background:transparent;border:none;cursor:pointer;padding:8px;display:flex;align-items:center;color:#60a5fa;border-radius:50%;transition:background 0.2s}" +
               ".sh-btn-go:hover{background:rgba(59,130,246,0.15)}" +
               ".home-slogan{display:none}";
    }

    private static String resultsCss() {
        return // Header
               ".r-header{display:flex;align-items:center;gap:24px;padding:20px 24px;background:#101116;border-bottom:1px solid #313745;position:sticky;top:0;z-index:200}" +
               ".r-logo{display:flex;align-items:center;gap:8px;text-decoration:none;flex-shrink:0}" +
               ".r-logo-box{display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 1px 8px rgba(59,130,246,0.3))}" +
               ".r-logo-name{font-size:20px;font-weight:700;letter-spacing:1px;color:#e2e8f0}" +
               ".r-bar-wrap{flex:1;max-width:720px;position:relative}" +
               ".r-search-shell{height:44px;padding:0 8px 0 16px;border-radius:22px}" +
               ".rsh-icon{display:none}" +
               ".rsh-input{background:transparent;border:none;outline:none;color:#e2e8f0;font-size:16px;font-family:inherit;flex:1;padding:0;min-width:0}" +
               ".rsh-clear,.rsh-voice{background:transparent;border:none;cursor:pointer;padding:8px;display:flex;align-items:center;border-radius:50%;color:#64748b;transition:background 0.2s}" +
               ".rsh-voice{color:#60a5fa}" +
               ".rsh-clear:hover,.rsh-voice:hover{background:rgba(59,130,246,0.15)}" +
               ".rsh-div{height:24px;width:1px;background:#313745;margin:0 12px 0 8px}" +
               ".rsh-go{background:transparent;border:none;cursor:pointer;padding:8px;display:flex;align-items:center;color:#60a5fa;border-radius:50%;transition:background 0.2s}" +
               ".rsh-go:hover{background:rgba(59,130,246,0.15)}" +
               // Tabs
               ".r-tabs{background:#101116;border-bottom:1px solid #313745;padding:0 0 0 24px}" +
               ".r-tabs-inner{display:flex;gap:24px;max-width:1200px;margin:0 auto;padding-top:10px}" +
               ".r-tab{display:flex;align-items:center;gap:6px;font-size:14px;color:#94a3b8;padding:10px 0 12px;border-bottom:3px solid transparent;cursor:pointer}" +
               ".r-tab:hover{color:#60a5fa}" +
               ".rt-active{color:#60a5fa !important;border-bottom-color:#60a5fa !important}" +
               // Body
               ".r-body{display:flex;gap:32px;padding:24px 24px 60px;max-width:1200px;margin:0 auto}" +
               ".r-main{flex:1;min-width:0;max-width:700px}" +
               ".r-main-full{max-width:100%}" +
               ".r-count{font-size:14px;color:#64748b;margin-bottom:24px}" +
               // Result cards
               ".rc{margin-bottom:28px}" +
               ".rc-head{display:flex;align-items:center;gap:12px;margin-bottom:8px}" +
               ".rc-fav{width:28px;height:28px;border-radius:50%;object-fit:cover;background:#1a1d24;padding:4px}" +
               ".rc-domain{font-size:14px;color:#cbd5e1;font-weight:400}" +
               ".rc-bread{font-size:12px;color:#94a3b8;margin-top:2px}" +
               ".rc-title{font-size:20px;color:#60a5fa;line-height:1.3;margin-bottom:4px;font-weight:400}" +
               ".rc-title:hover{text-decoration:underline}" +
               ".rc-snip{font-size:14px;color:#cbd5e1;line-height:1.58}" +
               ".rc-snip em{color:#e2e8f0;font-weight:600;font-style:normal}" +
               // Knowledge panel
               ".kp{width:340px;flex-shrink:0;background:#1a1d24;border:1px solid #313745;border-radius:12px;padding:24px;align-self:flex-start;position:sticky;top:90px}" +
               ".kp-top{display:flex;align-items:center;gap:16px;margin-bottom:16px}" +
               ".kp-ico{width:64px;height:64px;border-radius:8px;object-fit:contain;background:#161821}" +
               ".kp-name{font-size:22px;font-weight:400;color:#e2e8f0}" +
               ".kp-sub{font-size:14px;color:#94a3b8;margin-top:4px}" +
               ".kp-desc{font-size:14px;color:#cbd5e1;line-height:1.58;margin-bottom:24px;padding-bottom:24px;border-bottom:1px solid #313745}" +
               ".kp-facts{display:flex;flex-direction:column;gap:12px}" +
               ".kp-row{display:flex;gap:12px;font-size:14px}" +
               ".kp-key{color:#94a3b8;font-weight:700;min-width:80px;flex-shrink:0}" +
               ".kp-val{color:#60a5fa}" +
               // Pagination
               ".pag{display:flex;align-items:center;justify-content:center;gap:8px;margin-top:40px;padding-top:20px;flex-wrap:wrap}" +
               ".pag-n{width:32px;height:32px;display:flex;align-items:center;justify-content:center;border-radius:8px;font-size:14px;color:#60a5fa;cursor:pointer;background:#1a1d24;border:1px solid #313745}" +
               ".pag-n:hover{background:#2a2d36;text-decoration:none;border-color:#60a5fa}" +
               ".pag-cur{background:#2563eb !important;color:#fff !important;border-color:#2563eb !important;font-weight:500;cursor:default}" +
               ".pag-btn{font-size:14px;padding:8px 16px;color:#60a5fa;font-weight:500;cursor:pointer;background:#1a1d24;border:1px solid #313745;border-radius:20px}" +
               ".pag-btn:hover{background:#2a2d36;text-decoration:none;border-color:#60a5fa}" +
               // Image Grid
               ".img-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:16px;width:100%}" +
               ".ig-card{background:#161821;border-radius:12px;overflow:hidden;cursor:pointer;border:1px solid #313745;transition:border-color 0.2s, transform 0.2s}" +
               ".ig-card:hover{border-color:#60a5fa;transform:translateY(-2px)}" +
               ".ig-card:hover .ig-title{text-decoration:underline}" +
               ".ig-img{width:100%;height:160px;object-fit:cover;display:block;border-radius:12px 12px 0 0}" +
               ".ig-meta{display:flex;align-items:center;gap:8px;padding:12px}" +
               ".ig-fav{width:16px;height:16px;border-radius:2px;flex-shrink:0}" +
               ".ig-info{flex:1;overflow:hidden}" +
               ".ig-title{font-size:13px;color:#cbd5e1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
               ".ig-domain{font-size:11px;color:#94a3b8;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}" +
               // News cards
               ".nws{display:flex;gap:16px;padding:0;background:transparent;border:none;margin-bottom:28px}" +
               ".nws-l{flex:1}" +
               ".nws-src{display:flex;align-items:center;gap:8px;margin-bottom:6px}" +
               ".nws-fav{width:16px;height:16px;border-radius:50%;object-fit:contain;background:#1a1d24;padding:2px}" +
               ".nws-src span{font-size:12px;color:#cbd5e1}" +
               ".nws-title{display:block;font-size:20px;font-weight:400;color:#60a5fa;margin-bottom:6px;line-height:1.3}" +
               ".nws-title:hover{text-decoration:underline}" +
               ".nws-snip{font-size:14px;color:#cbd5e1;line-height:1.58;margin-bottom:8px}" +
               ".nws-time{font-size:12px;color:#94a3b8}" +
               ".nws-img{width:112px;height:112px;border-radius:12px;object-fit:cover;flex-shrink:0}" +
               // Video cards
               ".vid{display:flex;gap:16px;padding:0;background:transparent;border:none;margin-bottom:28px}" +
               ".vid-thumb{position:relative;width:240px;height:135px;flex-shrink:0;background:#0a0a14;cursor:pointer;border-radius:12px;overflow:hidden}" +
               ".vid-img{width:100%;height:100%;object-fit:cover;display:block;opacity:0.85}" +
               ".vid:hover .vid-img{opacity:1}" +
               ".vid-play-btn{position:absolute;bottom:8px;left:8px;width:32px;height:32px;background:rgba(0,0,0,0.75);border-radius:50%;display:flex;align-items:center;justify-content:center;padding-left:2px;border:1px solid rgba(255,255,255,0.2)}" +
               ".vid-info{flex:1;display:flex;flex-direction:column;justify-content:flex-start;padding-top:4px}" +
               ".vid-site{display:flex;align-items:center;gap:8px;font-size:12px;color:#cbd5e1;margin-bottom:6px}" +
               ".vid-fav{width:16px;height:16px;border-radius:50%;background:#1a1d24;padding:2px}" +
               ".vid-title{display:block;font-size:20px;font-weight:400;color:#60a5fa;line-height:1.3;margin-bottom:6px}" +
               ".vid-title:hover{text-decoration:underline}" +
               ".vid-snip{font-size:14px;color:#cbd5e1;line-height:1.58}";
    }

    private static String jsCode() {
        return "<script>" +
               // Voice Search
               "function startVoiceSearch(){" +
               "  if(!('webkitSpeechRecognition' in window)){alert('Voice search requires Chrome.');return;}" +
               "  var r=new webkitSpeechRecognition();r.lang='en-US';" +
               "  r.onstart=function(){var el=document.getElementById('si');if(el)el.placeholder='Listening\u2026';};" +
               "  r.onresult=function(e){var si=document.getElementById('si');if(si)si.value=e.results[0][0].transcript;document.getElementById('sf').submit();};" +
               "  r.onerror=function(){var el=document.getElementById('si');if(el)el.placeholder='Search\u2026';};" +
               "  r.start();}" +
               // Remove history item
               "var _hist=function(){};" +
               "function removeHist(e,item){" +
               "  e.stopPropagation();" +
               "  var h=JSON.parse(localStorage.getItem('_th')||'[]');" +
               "  h=h.filter(function(i){return i!==item;});" +
               "  localStorage.setItem('_th',JSON.stringify(h));" +
               "  _hist();}" +
               // DOMContentLoaded
               "document.addEventListener('DOMContentLoaded',function(){" +
               "  var si=document.getElementById('si');" +
               "  var sf=document.getElementById('sf');" +
               "  var dd=document.querySelector('.hist-drop')||document.querySelector('.hist-drop-res');" +
               "  var sb=document.getElementById('sb-container');" +
               "  var debounceTimer;" +
               // Show history
               "  function loadHist(){" +
               "    var h=JSON.parse(localStorage.getItem('_th')||'[]');" +
               "    if(!h.length||!dd){if(sb)sb.classList.remove('hist-open');if(dd)dd.style.display='none';return;}" +
               "    var html='';" +
               "    h.forEach(function(item){" +
               "      var s=item.replace(/\\\\/g,'\\\\\\\\').replace(/'/g,\"\\\\'\");" +
               "      html+='<div class=\"hist-row\"><svg class=\"hist-clk\" viewBox=\"0 0 24 24\" onclick=\"var si=document.getElementById(\\'si\\');si.value=\\''+s+'\\';document.getElementById(\\'sf\\').submit()\"><path d=\"M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z\" fill=\"#4a5568\"/></svg><span class=\"hist-txt\" onclick=\"var si=document.getElementById(\\'si\\');si.value=\\''+s+'\\';document.getElementById(\\'sf\\').submit()\">'+item+'</span><span class=\"hist-rm\" onclick=\"removeHist(event,\\''+s+'\\')\">\u00d7</span></div>';" +
               "    });" +
               "    if(dd){dd.innerHTML=html;dd.style.display='block';}if(sb)sb.classList.add('hist-open');}" +
               "  _hist=loadHist;" +
               // Autocomplete via /suggest
               "  function showSuggest(data){" +
               "    if(!data||!data.length){if(dd)dd.style.display='none';if(sb)sb.classList.remove('hist-open');return;}" +
               "    var html='';" +
               "    data.forEach(function(item){" +
               "      var s=item.replace(/'/g,\"\\\\'\");" +
               "      html+='<div class=\"hist-row\"><svg class=\"hist-clk\" viewBox=\"0 0 24 24\"><path d=\"M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z\" fill=\"#4a6a8a\"/></svg><span class=\"hist-txt\" onclick=\"var si=document.getElementById(\\'si\\');si.value=\\''+s+'\\';document.getElementById(\\'sf\\').submit()\">'+item+'</span></div>';" +
               "    });" +
               "    if(dd){dd.innerHTML=html;dd.style.display='block';}if(sb)sb.classList.add('hist-open');}" +
               // Input listeners
               "  if(si){" +
               "    si.addEventListener('focus',function(){if(!this.value.trim())loadHist();});" +
               "    si.addEventListener('input',function(){" +
               "      var q=this.value.trim();" +
               "      if(!q){loadHist();return;}" +
               "      clearTimeout(debounceTimer);" +
               "      debounceTimer=setTimeout(function(){" +
               "        fetch('/suggest?q='+encodeURIComponent(q)).then(function(r){return r.json();}).then(showSuggest).catch(function(){});" +
               "      },180);})}" +
               // Click outside to close
               "  document.addEventListener('click',function(e){" +
               "    if(dd&&sb&&!sb.contains(e.target)&&!dd.contains(e.target)){" +
               "      dd.style.display='none';sb.classList.remove('hist-open');}});" +
               // Save to history on submit
               "  if(sf)sf.addEventListener('submit',function(){" +
               "    if(!si||!si.value.trim())return;" +
               "    var h=JSON.parse(localStorage.getItem('_th')||'[]');" +
               "    var v=si.value.trim();" +
               "    h=h.filter(function(i){return i!==v;});" +
               "    h.unshift(v);if(h.length>10)h.pop();" +
               "    localStorage.setItem('_th',JSON.stringify(h));});" +
               "});</script>";
    }

    // ─── SVG ICONS ───────────────────────────────────────────────────────────────
    private static String svgSearchB() {
        return "<svg width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='#09090b' stroke-width='2' stroke-linecap='round'><circle cx='11' cy='11' r='8'/><path d='m21 21-4.35-4.35'/></svg>";
    }
    private static String svgSearchW() {
        return "<svg width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='#fff' stroke-width='2' stroke-linecap='round'><circle cx='11' cy='11' r='8'/><path d='m21 21-4.35-4.35'/></svg>";
    }
    private static String svgSearch() {
        return "<svg width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round'><circle cx='11' cy='11' r='8'/><path d='m21 21-4.35-4.35'/></svg>";
    }
    private static String svgMic() {
        return "<svg width='18' height='18' viewBox='0 0 24 24'><rect x='9' y='2' width='6' height='11' rx='3' fill='currentColor'/><path d='M5 10a7 7 0 0 0 14 0' stroke='currentColor' stroke-width='2' fill='none'/><line x1='12' y1='19' x2='12' y2='22' stroke='currentColor' stroke-width='2'/></svg>";
    }
    private static String svgX() {
        return "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round'><line x1='18' y1='6' x2='6' y2='18'/><line x1='6' y1='6' x2='18' y2='18'/></svg>";
    }

    // ─── UTILS ───────────────────────────────────────────────────────────────────
    private static String firstImage(Map<?,?> src) {
        List<?> imgs = (List<?>) src.get("images");
        if (imgs != null && !imgs.isEmpty()) return imgs.get(0).toString();
        return "";
    }
    private static void tryReconnect() { try { if (esClient!=null){esClient.info();esConnected=true;} } catch(Exception ignored){} }
    private static String esc(String s) { return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    private static String escUrl(String s) { try{return s==null?"":URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";} }
    private static String safe(Map<?,?> m, String k) { Object v=m.get(k); return v==null?"":v.toString(); }
}