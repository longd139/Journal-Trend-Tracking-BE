package com.sra.journal_tracking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Finds academically related keywords using DeepSeek AI with local fallback.
 * When the API is unavailable (quota, network), falls back to a built-in
 * academic keyword relationship map organized by research domains.
 */
@Slf4j
@Service
public class GeminiService {

    private static final int DEFAULT_MAX_RELATED = 6;
    private static final int KEYWORD_MAX_TOKENS = 256;
    private static final double KEYWORD_TEMPERATURE = 0.2;
    private static final int MAX_RETRIES = 0; // No retries — RestTemplate has short timeout, fallback to local

    // ── In-memory cache: 1 hour TTL ──
    private static final long CACHE_TTL_MS = 60 * 60 * 1000;

    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data) { this.data = data; this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS; }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }

    private final ConcurrentHashMap<String, CacheEntry<List<String>>> relatedKeywordsCache = new ConcurrentHashMap<>();

    private final AIClient aiClient;
    private final KeywordExpansionService keywordExpansionService;

    public GeminiService(AIClient aiClient,
                         KeywordExpansionService keywordExpansionService) {
        this.aiClient = aiClient;
        this.keywordExpansionService = keywordExpansionService;
    }

    /**
     * Get academically related keywords. Tries DeepSeek first,
     * falls back to local expansion if the API is unavailable.
     * Results are cached for 1 hour — subsequent calls for the same keyword
     * return instantly without any API call.
     */
    public List<String> getRelatedKeywords(String keyword, int maxTerms) {
        String cacheKey = keyword.toLowerCase().trim();

        // ── Cache hit? ──
        CacheEntry<List<String>> cached = relatedKeywordsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            List<String> result = cached.data.stream()
                    .limit(Math.max(1, Math.min(maxTerms, DEFAULT_MAX_RELATED)))
                    .collect(Collectors.toList());
            log.info("CACHE HIT: relatedKeywords '{}' → {} terms (from cache)", keyword, result.size());
            return result;
        }
        if (cached != null) {
            relatedKeywordsCache.remove(cacheKey); // expired → remove
        }

        int limit = Math.max(1, Math.min(maxTerms, DEFAULT_MAX_RELATED));

        // ── Try AI API ──
        List<String> result;
        try {
            result = tryAiExpansion(keyword, limit);
            if (!result.isEmpty()) {
                // Cache and return
                relatedKeywordsCache.put(cacheKey, new CacheEntry<>(new ArrayList<>(result)));
                log.info("CACHE STORE: relatedKeywords '{}' → {} terms (TTL=1h, source=AI)", keyword, result.size());
                return result;
            }
        } catch (Exception e) {
            log.info("AI unavailable for '{}', using local fallback. Reason: {}", keyword, e.getMessage());
        }

        // ── Local fallback ──
        log.info("Using local keyword expansion for '{}'", keyword);
        result = localExpansion(keyword, limit);

        // ── Cache local results too ──
        if (!result.isEmpty()) {
            relatedKeywordsCache.put(cacheKey, new CacheEntry<>(new ArrayList<>(result)));
            log.info("CACHE STORE: relatedKeywords '{}' → {} terms (TTL=1h, source=local)", keyword, result.size());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  AI API call (via AIClient — currently DeepSeek)
    // ═══════════════════════════════════════════════════════════

    private List<String> tryAiExpansion(String keyword, int limit) throws Exception {
        String prompt = buildKeywordPrompt(keyword, limit);
        String responseText = aiClient.call(prompt, KEYWORD_MAX_TOKENS, KEYWORD_TEMPERATURE);
        List<String> keywords = parseKeywords(responseText, limit);
        if (!keywords.isEmpty()) {
            log.info("AI expanded '{}' → {}", keyword, keywords);
            return keywords;
        }
        return keywords;
    }

    private String buildKeywordPrompt(String keyword, int limit) {
        return String.format(
                "You are an academic research assistant. " +
                "Given the research keyword \"%s\", list exactly %d closely related academic/research keywords or terminology. " +
                "Rules:\n" +
                "1. Return ONLY the keywords, one per line, no numbering, no bullet points, no explanations.\n" +
                "2. Keywords should be specific academic/research terms commonly co-occurring with \"%s\".\n" +
                "3. Include synonyms, sub-topics, related technologies, methods, or concepts.\n" +
                "4. Each keyword should be 1-4 words, lowercase preferred.\n" +
                "5. Do NOT repeat the input keyword.\n" +
                "6. Prioritize terms that appear in research paper titles and abstracts.",
                keyword, limit, keyword);
    }

    private List<String> parseKeywords(String rawText, int maxTerms) {
        return Arrays.stream(rawText.split("\\n"))
                .map(String::trim)
                .map(line -> line.replaceAll("^[\\d]+[\\.\\)\\-\\s]+", ""))
                .map(line -> line.replaceAll("^[-\\*\\.•]+\\s*", ""))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.length() >= 2)
                .filter(line -> line.length() <= 100)
                .distinct()
                .limit(maxTerms)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    //  Local fallback expansion (no API needed)
    // ═══════════════════════════════════════════════════════════

    /**
     * Local keyword expansion using built-in academic relationships + synonym service.
     * Returns up to maxTerms related keywords for the given input.
     */
    private List<String> localExpansion(String keyword, int maxTerms) {
        String normalized = keyword.toLowerCase().trim();
        List<String> results = new ArrayList<>();

        // 1. Check built-in academic relationship map
        List<String> mapped = ACADEMIC_RELATIONS.get(normalized);
        if (mapped != null) {
            results.addAll(mapped);
        }

        // 2. Check synonym service for broader matches
        try {
            keywordExpansionService.expand(keyword, maxTerms)
                    .stream()
                    .filter(term -> !term.equalsIgnoreCase(normalized))
                    .filter(term -> !results.contains(term))
                    .forEach(results::add);
        } catch (Exception ignored) {}

        // 3. Try partial word match from the relation map
        if (results.size() < 2) {
            for (Map.Entry<String, List<String>> entry : ACADEMIC_RELATIONS.entrySet()) {
                if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
                    entry.getValue().stream()
                            .filter(t -> !t.equalsIgnoreCase(normalized))
                            .filter(t -> !results.contains(t))
                            .forEach(results::add);
                }
            }
        }

        return results.stream().distinct().limit(maxTerms).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    //  Academic keyword relationship map (organized by domain)
    // ═══════════════════════════════════════════════════════════

    private static final Map<String, List<String>> ACADEMIC_RELATIONS = Map.ofEntries(
            // ── Computer Science / AI ──
            Map.entry("machine learning", List.of("deep learning", "neural network", "supervised learning", "reinforcement learning", "natural language processing", "computer vision")),
            Map.entry("deep learning", List.of("neural network", "convolutional network", "transformer", "backpropagation", "gradient descent", "activation function")),
            Map.entry("artificial intelligence", List.of("machine learning", "neural network", "natural language processing", "robotics", "expert system", "computer vision")),
            Map.entry("neural network", List.of("deep learning", "backpropagation", "convolutional layer", "activation function", "perceptron", "gradient descent")),
            Map.entry("natural language processing", List.of("transformer", "tokenization", "sentiment analysis", "named entity recognition", "language model", "text classification")),
            Map.entry("computer vision", List.of("convolutional network", "image recognition", "object detection", "image segmentation", "feature extraction", "optical flow")),
            Map.entry("reinforcement learning", List.of("q-learning", "policy gradient", "markov decision process", "reward function", "exploration", "actor-critic")),
            Map.entry("large language model", List.of("transformer", "attention mechanism", "fine-tuning", "prompt engineering", "tokenization", "generative ai")),
            Map.entry("transformer", List.of("attention mechanism", "self-attention", "encoder-decoder", "positional encoding", "multi-head attention", "bert")),
            Map.entry("data mining", List.of("clustering", "classification", "association rule", "outlier detection", "frequent pattern", "feature selection")),

            // ── Data Science / Statistics ──
            Map.entry("statistics", List.of("regression analysis", "hypothesis testing", "probability distribution", "bayesian inference", "statistical significance", "variance analysis")),
            Map.entry("data analysis", List.of("regression", "clustering", "visualization", "feature engineering", "statistical test", "correlation")),
            Map.entry("big data", List.of("hadoop", "spark", "data warehouse", "distributed computing", "mapreduce", "stream processing")),

            // ── Automotive / Mechanical ──
            Map.entry("car", List.of("automobile", "vehicle", "engine", "tire", "brake system", "fuel efficiency")),
            Map.entry("automobile", List.of("vehicle", "engine", "transmission", "chassis", "combustion", "emission control")),
            Map.entry("engine", List.of("combustion", "piston", "cylinder", "fuel injection", "crankshaft", "turbocharger")),
            Map.entry("tire", List.of("rubber", "tread", "friction", "pressure monitoring", "wheel alignment", "rolling resistance")),
            Map.entry("brake", List.of("friction", "disc brake", "brake pad", "hydraulic system", "abs", "stopping distance")),
            Map.entry("electric vehicle", List.of("battery", "electric motor", "charging station", "regenerative braking", "lithium-ion", "powertrain")),
            Map.entry("autonomous vehicle", List.of("lidar", "sensor fusion", "path planning", "computer vision", "object detection", "control system")),
            Map.entry("combustion", List.of("fuel", "ignition", "exhaust", "cylinder pressure", "heat release", "emission")),

            // ── Biology / Medicine ──
            Map.entry("cancer", List.of("tumor", "chemotherapy", "metastasis", "oncogene", "immunotherapy", "radiotherapy")),
            Map.entry("gene", List.of("genome", "dna", "rna", "mutation", "gene expression", "genetic engineering")),
            Map.entry("protein", List.of("amino acid", "enzyme", "folding", "catalysis", "peptide", "receptor")),
            Map.entry("cell", List.of("membrane", "mitochondria", "nucleus", "organelle", "apoptosis", "stem cell")),
            Map.entry("virus", List.of("infection", "vaccine", "antiviral", "host cell", "pathogen", "immune response")),
            Map.entry("dna", List.of("rna", "nucleotide", "double helix", "replication", "transcription", "mutation")),

            // ── Physics ──
            Map.entry("quantum", List.of("superposition", "entanglement", "qubit", "wave function", "decoherence", "quantum gate")),
            Map.entry("semiconductor", List.of("silicon", "transistor", "band gap", "doping", "electron mobility", "wafer")),
            Map.entry("optics", List.of("laser", "photon", "refraction", "wavelength", "fiber optic", "interference")),

            // ── Chemistry ──
            Map.entry("polymer", List.of("monomer", "polymerization", "plastic", "cross-linking", "molecular weight", "biodegradable")),
            Map.entry("catalyst", List.of("reaction rate", "enzyme", "activation energy", "heterogeneous", "homogeneous", "surface area")),
            Map.entry("nanomaterial", List.of("nanoparticle", "carbon nanotube", "graphene", "quantum dot", "surface area", "nanocomposite")),

            // ── Environment / Energy ──
            Map.entry("climate change", List.of("global warming", "greenhouse gas", "carbon emission", "sea level", "temperature rise", "carbon footprint")),
            Map.entry("renewable energy", List.of("solar power", "wind turbine", "hydropower", "geothermal", "photovoltaic", "energy storage")),
            Map.entry("sustainability", List.of("renewable", "carbon neutral", "circular economy", "green technology", "resource efficiency", "sustainable development")),
            Map.entry("solar energy", List.of("photovoltaic", "solar cell", "solar panel", "perovskite", "energy conversion", "thin film")),

            // ── Economics / Business ──
            Map.entry("economics", List.of("supply demand", "market equilibrium", "inflation", "monetary policy", "fiscal policy", "gdp")),
            Map.entry("blockchain", List.of("cryptocurrency", "distributed ledger", "smart contract", "decentralization", "consensus algorithm", "bitcoin")),
            Map.entry("supply chain", List.of("logistics", "inventory management", "procurement", "distribution network", "warehouse", "demand forecasting")),

            // ── Psychology / Neuroscience ──
            Map.entry("cognition", List.of("memory", "attention", "perception", "learning", "decision making", "executive function")),
            Map.entry("neuroscience", List.of("neuron", "synapse", "brain imaging", "neurotransmitter", "cortex", "fmri")),

            // ── Materials Science ──
            Map.entry("graphene", List.of("carbon", "2d material", "conductivity", "mechanical strength", "nanotube", "flexible electronics")),
            Map.entry("corrosion", List.of("oxidation", "rust", "electrochemical", "protective coating", "metal degradation", "passivation")),
            Map.entry("alloy", List.of("metal", "microstructure", "heat treatment", "mechanical property", "phase diagram", "casting")),

            // ── Mathematics ──
            Map.entry("optimization", List.of("gradient descent", "convex function", "linear programming", "constraint", "objective function", "global minimum")),
            Map.entry("algorithm", List.of("complexity", "sorting", "graph theory", "dynamic programming", "recursion", "data structure")),

            // ── Short/catch-all entries for common single-word queries ──
            Map.entry("ai", List.of("machine learning", "deep learning", "neural network", "natural language processing")),
            Map.entry("ml", List.of("machine learning", "deep learning", "supervised learning", "neural network")),
            Map.entry("nlp", List.of("natural language processing", "text mining", "sentiment analysis", "language model")),
            Map.entry("cv", List.of("computer vision", "image processing", "object detection", "image segmentation")),
            Map.entry("ev", List.of("electric vehicle", "battery", "electric motor", "charging infrastructure")),
            Map.entry("iot", List.of("internet of things", "sensor network", "embedded system", "smart device", "edge computing")),
            Map.entry("cloud", List.of("cloud computing", "aws", "virtualization", "serverless", "distributed system", "container")),
            Map.entry("cybersecurity", List.of("encryption", "malware", "network security", "vulnerability", "authentication", "firewall")),

            // ── Business / Management / Finance ──
            Map.entry("corporate governance", List.of("board of directors", "shareholder rights", "executive compensation", "audit committee", "corporate social responsibility", "stakeholder theory")),
            Map.entry("management", List.of("leadership", "organizational behavior", "strategic planning", "human resources", "performance management", "change management")),
            Map.entry("entrepreneurship", List.of("startup", "venture capital", "business model", "innovation", "small business", "lean startup")),
            Map.entry("marketing", List.of("consumer behavior", "brand management", "digital marketing", "market segmentation", "advertising", "social media marketing")),
            Map.entry("finance", List.of("investment", "risk management", "capital market", "portfolio theory", "corporate finance", "financial modeling")),
            Map.entry("accounting", List.of("financial reporting", "auditing", "tax compliance", "internal control", "cost accounting", "financial statement")),
            Map.entry("strategy", List.of("competitive advantage", "business model", "market analysis", "disruption", "blue ocean strategy", "swot analysis")),
            Map.entry("human resource", List.of("employee engagement", "talent management", "recruitment", "workplace diversity", "compensation", "training development")),
            Map.entry("innovation", List.of("research development", "technology transfer", "disruptive technology", "patent", "product development", "open innovation")),
            Map.entry("risk management", List.of("credit risk", "market risk", "operational risk", "hedging", "basel", "enterprise risk")),
            Map.entry("business ethics", List.of("corporate social responsibility", "whistleblowing", "code of conduct", "sustainability reporting", "ethical leadership", "stakeholder engagement"))

    );
}
