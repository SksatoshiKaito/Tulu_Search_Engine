package org.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.StringReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WebCrawler {

    // ================== CONFIG ==================
    private static final String REDIS_QUEUE  = "tulu_queue_v3";
    private static final String REDIS_VISITED = "tulu_visited_v3";
    private static final String REDIS_DOMAIN_COUNT = "tulu_domain_count_v3";

    private static final int THREAD_COUNT = 6;
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int MAX_PAGES_PER_PRIORITY_DOMAIN = 200; // আপনার সাইটগুলো ২০০ পেজ
    private static final int MAX_PAGES_PER_OTHER_DOMAIN = 30;     // অন্য সাইটগুলো ৩০ পেজ

    // উইকিপিডিয়া এবং এই ডোমেইনগুলো সম্পূর্ণভাবে বাদ
    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
        "wikipedia.org", "wikimedia.org", "wikidata.org", "mediawiki.org",
        "wikisource.org", "wiktionary.org", "wikiquote.org", "wikinews.org",
        "wikiversity.org", "wikibooks.org", "en.m.wikipedia.org"
    ));

    // আপনার দেওয়া প্রায়োরিটি সাইটগুলো
    private static final Set<String> PRIORITY_DOMAINS = new HashSet<>(Arrays.asList(
        "aust.edu", "www.aust.edu",
        "hojaifa.web.app",
        "github.com", "www.github.com",
        "youtube.com", "www.youtube.com",
        "facebook.com", "www.facebook.com",
        "gemini.google.com",
        "w3schools.com", "www.w3schools.com"
    ));

    private static final AtomicInteger totalIndexed = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  TULU Search Crawler v3.0 Starting...    ║");
        System.out.println("╚══════════════════════════════════════════╝");

        String esHost = System.getenv("ES_HOST") != null ? System.getenv("ES_HOST") : "localhost";
        int esPort = System.getenv("ES_PORT") != null ? Integer.parseInt(System.getenv("ES_PORT")) : 9200;

        // Connect to Elasticsearch
        RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort)).build();
        ElasticsearchClient esClient = new ElasticsearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper())
        );

        // Initialize index only if it does not exist (prevents data loss)
        setupIndexIfMissing(esClient);

        String redisHost = System.getenv("REDISHOST") != null ? System.getenv("REDISHOST") : "localhost";
        int redisPort = System.getenv("REDISPORT") != null ? Integer.parseInt(System.getenv("REDISPORT")) : 6379;
        String redisPass = System.getenv("REDISPASSWORD");

        // Connect to Redis
        JedisPool jedisPool;
        if (redisPass != null && !redisPass.isEmpty()) {
            jedisPool = new JedisPool(new redis.clients.jedis.JedisPoolConfig(), redisHost, redisPort, 2000, redisPass);
        } else {
            jedisPool = new JedisPool(redisHost, redisPort);
        }

        // Resume crawl state from Redis (do not flush)
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.llen(REDIS_QUEUE) == 0 && !jedis.exists(REDIS_VISITED)) {
                System.out.println("🌱 Starting fresh crawl, adding priority seeds...");
                
                // Add priority seeds FIRST — they go at the BACK of the queue (processed first with rpop)
                String[] prioritySeeds = {
                    "https://www.aust.edu/",
                    "https://www.aust.edu/academics",
                    "https://www.aust.edu/departments",
                    "https://hojaifa.web.app/",
                    "https://github.com/explore",
                    "https://github.com/topics",
                    "https://github.com/trending",
                    "https://www.w3schools.com/html/",
                    "https://www.w3schools.com/css/",
                    "https://www.w3schools.com/js/",
                    "https://www.w3schools.com/python/",
                    "https://www.w3schools.com/java/",
                    "https://www.youtube.com/",
                    "https://www.facebook.com/",
                    "https://gemini.google.com/app"
                };

                // rpush means they'll be picked up first by rpop
                for (String seed : prioritySeeds) {
                    jedis.rpush(REDIS_QUEUE, seed);
                }
                System.out.println("✅ " + prioritySeeds.length + " priority seeds queued.");
            } else {
                System.out.println("♻️ Resuming previous crawl. Queue size: " + jedis.llen(REDIS_QUEUE));
            }
        }

        // Launch crawler threads
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        System.out.println("🚀 Launching " + THREAD_COUNT + " crawler threads...\n");

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i + 1;
            executor.submit(() -> crawlLoop(jedisPool, esClient, threadId));
        }

        // Print stats every 30 seconds
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> {
            System.out.println("\n📊 Total pages indexed: " + totalIndexed.get());
            try (Jedis jedis = jedisPool.getResource()) {
                System.out.println("📋 Pages in queue: " + jedis.llen(REDIS_QUEUE));
            } catch (Exception ignored) {}
        }, 30, 30, TimeUnit.SECONDS);

        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    // ================== CRAWL LOOP ==================
    private static void crawlLoop(JedisPool jedisPool, ElasticsearchClient esClient, int threadId) {
        try (Jedis jedis = jedisPool.getResource()) {
            while (true) {
                String urlStr = jedis.rpop(REDIS_QUEUE);

                if (urlStr == null) {
                    Thread.sleep(3000);
                    continue;
                }

                try {
                    URL urlObj = new URL(urlStr);
                    String domain = urlObj.getHost().replaceFirst("^www\\.", "");

                    // BLOCK: skip wikipedia and other blocked domains
                    if (isBlockedDomain(domain)) {
                        continue;
                    }

                    // Check domain page limit
                    boolean isPriority = PRIORITY_DOMAINS.stream().anyMatch(pd -> domain.contains(pd) || pd.contains(domain));
                    int limit = isPriority ? MAX_PAGES_PER_PRIORITY_DOMAIN : MAX_PAGES_PER_OTHER_DOMAIN;

                    long domainCount = jedis.hincrBy(REDIS_DOMAIN_COUNT, domain, 1);
                    if (domainCount > limit) {
                        continue; // this domain has enough pages
                    }

                    // Visit only if not already seen
                    if (jedis.sadd(REDIS_VISITED, urlStr) == 0) {
                        continue; // already visited
                    }

                    // Fetch & parse the page
                    Document doc = Jsoup.connect(urlStr)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .userAgent("TuluBot/3.0 (+https://tulu.search)")
                        .followRedirects(true)
                        .get();

                    String title = doc.title();
                    String content = doc.text();

                    // Skip pages with very little content
                    if (content.length() < 100) continue;

                    // Truncate content to 10KB max to save space
                    if (content.length() > 10000) content = content.substring(0, 10000);

                    // Extract Images
                    List<String> imageUrls = new ArrayList<>();
                    Elements imgElements = doc.select("img[src]");
                    for (org.jsoup.nodes.Element img : imgElements) {
                        String src = img.absUrl("src");
                        if (src.startsWith("http") && (src.endsWith(".jpg") || src.endsWith(".png") || src.endsWith(".jpeg") || src.endsWith(".webp") || src.endsWith(".gif"))) {
                            imageUrls.add(src);
                        }
                    }
                    if (imageUrls.size() > 10) imageUrls = imageUrls.subList(0, 10);

                    // Extract Knowledge Graph / Rich Snippets data
                    String schemaJson = "";
                    org.jsoup.nodes.Element ldJson = doc.selectFirst("script[type=application/ld+json]");
                    if (ldJson != null) {
                        schemaJson = ldJson.data();
                        if (schemaJson.length() > 5000) schemaJson = schemaJson.substring(0, 5000);
                    }
                    String ogImage = "";
                    org.jsoup.nodes.Element metaImg = doc.selectFirst("meta[property=og:image]");
                    if (metaImg != null) ogImage = metaImg.attr("content");
                    String ogDesc = "";
                    org.jsoup.nodes.Element metaDesc = doc.selectFirst("meta[property=og:description]");
                    if (metaDesc != null) ogDesc = metaDesc.attr("content");

                    long timestamp = System.currentTimeMillis();
                    int urlDepth = urlStr.split("/").length - 3; // subtract http, empty, domain

                    // Index in Elasticsearch
                    Map<String, Object> data = new HashMap<>();
                    data.put("title", title);
                    data.put("url", urlStr);
                    data.put("domain", domain);
                    data.put("content", content);
                    data.put("images", imageUrls);
                    data.put("timestamp", timestamp);
                    data.put("url_depth", urlDepth < 0 ? 0 : urlDepth);
                    data.put("schema_json", schemaJson);
                    data.put("og_image", ogImage);
                    data.put("og_desc", ogDesc);

                    final Map<String, Object> finalData = data;
                    esClient.index(req -> req.index("search_index").document(finalData));

                    int count = totalIndexed.incrementAndGet();
                    System.out.printf("[T%d] ✓ (%d) %s — %s%n", threadId, count, domain, title.length() > 60 ? title.substring(0, 60) + "..." : title);

                    // Add outbound links to queue
                    Elements links = doc.select("a[href]");
                    for (var link : links) {
                        String next = link.attr("abs:href");
                        if (next.startsWith("http") && !next.contains("#")) {
                            // Filter out media files
                            if (!next.matches(".*\\.(jpg|jpeg|png|gif|pdf|zip|mp4|mp3|svg|ico|woff|css|js)$")) {
                                jedis.lpush(REDIS_QUEUE, next);
                            }
                        }
                    }

                    // Polite crawl delay
                    Thread.sleep(isPriority ? 300 : 800);

                } catch (Exception e) {
                    // Silently skip errors (timeouts, 403s, etc.)
                }
            }
        } catch (Exception e) {
            System.err.println("[FATAL] Thread error: " + e.getMessage());
        }
    }

    // ================== DOMAIN BLOCK CHECK ==================
    private static boolean isBlockedDomain(String domain) {
        for (String blocked : BLOCKED_DOMAINS) {
            if (domain.contains(blocked)) return true;
        }
        return false;
    }

    // ================== ELASTICSEARCH SETUP ==================
    private static void setupIndexIfMissing(ElasticsearchClient esClient) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index("search_index")).value();
            if (exists) {
                System.out.println("✅ Existing Elasticsearch index found. Resuming...");
                return;
            }

            System.out.println("⚙  Creating new search index...");
            String mapping = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                  },
                  "mappings": {
                    "properties": {
                      "title":       { "type": "text", "analyzer": "english" },
                      "url":         { "type": "text", "analyzer": "simple" },
                      "domain":      { "type": "keyword" },
                      "content":     { "type": "text", "analyzer": "english" },
                      "images":      { "type": "keyword" },
                      "timestamp":   { "type": "date", "format": "epoch_millis" },
                      "url_depth":   { "type": "integer" },
                      "schema_json": { "type": "keyword", "index": false },
                      "og_image":    { "type": "keyword", "index": false },
                      "og_desc":     { "type": "text", "index": false }
                    }
                  }
                }
                """;
            esClient.indices().create(c -> c.index("search_index").withJson(new StringReader(mapping)));
            System.out.println("✅ Index created successfully!\n");
        } catch (Exception e) {
            System.err.println("❌ Index setup error: " + e.getMessage());
        }
    }
}