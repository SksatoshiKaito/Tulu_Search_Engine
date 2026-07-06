<div align="center">

<img src="https://img.shields.io/badge/TULU-Cognitive%20Search%20Engine-blue?style=for-the-badge&logo=elasticsearch&logoColor=white" alt="TULU Search Engine"/>

# 🔍 TULU — Cognitive Search Engine

**A fully functional, self-hosted search engine built from scratch in Java.**  
Crawls the web, indexes content, and delivers instant search results — just like Google.

[![Java](https://img.shields.io/badge/Java-17+-orange?style=flat-square&logo=java)](https://java.com)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.x-brightgreen?style=flat-square&logo=elasticsearch)](https://elastic.co)
[![Redis](https://img.shields.io/badge/Redis-7.x-red?style=flat-square&logo=redis)](https://redis.io)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)](https://docker.com)
[![Cloudflare](https://img.shields.io/badge/Cloudflare-Tunnel-orange?style=flat-square&logo=cloudflare)](https://cloudflare.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

<br>

</div>

---

## 🌟 What is TULU?

TULU is a **production-grade, self-hosted web search engine** built entirely from scratch using Java. It autonomously crawls the internet, indexes web pages, and serves search results through a beautiful, responsive web interface — all running on your own machine or server.

This project demonstrates mastery of:
- Distributed systems design
- Full-text search & indexing
- Multi-threaded web crawling
- Real-time data pipelines
- Cloud deployment & tunneling

> **"I didn't just use Google — I built one."**

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    TULU Search Engine                        │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  Web Crawler │───▶│    Redis     │───▶│  URL Queue   │  │
│  │  (6 Threads) │    │   Frontier   │    │  & Visited   │  │
│  └──────┬───────┘    └──────────────┘    └──────────────┘  │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Jsoup      │───▶│Elasticsearch │───▶│  Full-Text   │  │
│  │   Parser     │    │   Indexer    │    │    Index     │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│                                                   │         │
│                                                   ▼         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Browser    │◀───│  Java HTTP   │◀───│   Search     │  │
│  │    User      │    │   Server     │    │    API       │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## ✨ Features

| Feature | Description |
|---|---|
| 🕷️ **Multi-threaded Crawler** | 6 concurrent threads crawl the web simultaneously |
| 🔍 **Full-Text Search** | Powered by Elasticsearch with BM25 relevance ranking |
| 📊 **40,000+ Pages Indexed** | Over 40,000 web pages crawled and searchable |
| 🎯 **Smart URL Frontier** | Redis-based priority queue with domain throttling |
| 🔄 **Resumable Crawling** | Survives restarts — resumes exactly where it stopped |
| 🖼️ **Image Search** | Searches and displays images from indexed pages |
| 📰 **News Tab** | Dedicated news content aggregation |
| 🎙️ **Voice Search** | Microphone-based voice input support |
| 🌐 **Public Hosting** | Cloudflare Tunnel for zero-config public access |
| 🐳 **Docker Support** | One-command deployment with Docker Compose |
| 📱 **Responsive UI** | Beautiful dark-themed UI, works on all devices |

---

## 🛠️ Tech Stack

### Backend
- **Java 17** — Core application language
- **Elasticsearch 8.x** — Search indexing and full-text retrieval
- **Redis 7.x** — URL frontier queue and visited URL tracking
- **Jsoup** — HTML parsing and link extraction
- **Java HTTP Server** — Lightweight built-in HTTP server

### Frontend
- **HTML5 / CSS3 / JavaScript** — Pure vanilla frontend
- **Custom Dark Theme UI** — Google-inspired clean design
- **Voice Recognition API** — Web Speech API integration

### Infrastructure
- **Docker & Docker Compose** — Container orchestration
- **Cloudflare Tunnel** — Secure public internet exposure
- **Railway.app** — Cloud deployment support
- **Maven** — Build and dependency management

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker Desktop
- Maven 3.8+

### 1. Clone the Repository
```bash
git clone https://github.com/SksatoshiKaito/Tulu-Search-Engine.git
cd Tulu-Search-Engine
```

### 2. Start Databases
```bash
docker-compose up -d
```

### 3. Run the Search Engine
```bash
mvn exec:java -Dexec.mainClass=org.example.Main
```

### 4. Open in Browser
```
http://localhost:8082
```

---

## 🌐 Make It Public (Cloudflare Tunnel)

To expose your local search engine to the internet for free:

```bash
# Start the tunnel
cloudflared.exe tunnel --url http://localhost:8082
```

You'll get a public URL like:
```
https://your-unique-name.trycloudflare.com
```

---

## 📁 Project Structure

```
WebCrawler/
├── src/
│   └── main/
│       └── java/
│           └── org/example/
│               ├── Main.java          # HTTP Server & Search API
│               └── WebCrawler.java    # Multi-threaded Web Crawler
├── docker-compose.yml                 # Elasticsearch + Redis setup
├── Dockerfile                         # Production container build
├── pom.xml                            # Maven dependencies
├── cloudflared.exe                    # Cloudflare Tunnel binary
├── README.md                          # Project Documentation
└── start_tulu_online.bat              # One-click launcher (Windows)
```

---

## 🔧 Configuration

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ES_HOST` | `localhost` | Elasticsearch host |
| `ES_PORT` | `9200` | Elasticsearch port |
| `REDISHOST` | `localhost` | Redis host |
| `REDISPORT` | `6379` | Redis port |
| `PORT` | `8082` | HTTP server port |

---

## 📊 Performance

- **Crawl Speed:** ~200-500 pages/minute with 6 threads
- **Index Size:** 40,000+ documents indexed
- **Search Latency:** < 100ms average response time
- **Memory Usage:** ~512MB RAM (JVM + Elasticsearch)

---

## 🧠 How It Works

### 1. Web Crawling
The crawler starts from seed URLs and follows links across the web. It uses a **priority queue** in Redis to ensure important domains are crawled first. A **visited set** prevents re-crawling the same page.

### 2. Indexing
Each crawled page is parsed with Jsoup. The **title**, **content**, **URL**, **domain**, and any **images** are extracted and stored in Elasticsearch with full-text indexing.

### 3. Search
When a user types a query, the Java HTTP server sends a **multi-match query** to Elasticsearch across title and content fields. Results are ranked by relevance using BM25 scoring and returned as JSON.

### 4. Resilience
The system is designed to **never lose data**. On restart, it checks if the Elasticsearch index exists before creating it. The Redis queue resumes from its last state, so no URLs are re-crawled unnecessarily.


---


<img width="1600" height="899" alt="WhatsApp Image 2026-07-03 at 12 07 15 PM" src="https://github.com/user-attachments/assets/338651bc-05ee-4c20-94b4-34e5a7e300a9" />

