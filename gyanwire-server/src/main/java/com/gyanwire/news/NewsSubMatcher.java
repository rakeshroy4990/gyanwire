package com.gyanwire.news;

import java.util.Locale;
import java.util.Map;

/**
 * When a sub chip is selected, parent industry rows (sub null) must still match
 * that specialty by title/summary — otherwise every chip fills with generic news.
 */
final class NewsSubMatcher {

    private NewsSubMatcher() {
    }

    static boolean matches(String industry, String sub, NewsRow row) {
        if (row == null || sub == null || sub.isBlank()) {
            return false;
        }
        if (sub.equals(row.sub())) {
            return true;
        }
        if (row.sub() != null && !row.sub().isBlank()) {
            return false;
        }
        String blob = blob(row);
        String[] terms = termsFor(industry, sub);
        if (terms != null) {
            return containsAny(blob, terms);
        }
        return blob.contains(sub.toLowerCase(Locale.ROOT));
    }

    private static String blob(NewsRow row) {
        return ((row.title() == null ? "" : row.title()) + " "
                + (row.summary() == null ? "" : row.summary())).toLowerCase(Locale.ROOT);
    }

    private static String[] termsFor(String industry, String sub) {
        if (industry == null || industry.isBlank()) {
            return null;
        }
        Map<String, String[]> bySub = switch (industry) {
            case "AI" -> AI;
            case "IT" -> IT;
            case "Medical" -> MEDICAL;
            case "Space" -> SPACE;
            case "Share Market" -> SHARE_MARKET;
            case "Social Media" -> SOCIAL_MEDIA;
            case "Gaming" -> GAMING;
            case "Real Estate" -> REAL_ESTATE;
            case "Astrology" -> ASTROLOGY;
            default -> null;
        };
        if (bySub == null) {
            return null;
        }
        return bySub.get(sub);
    }

    private static boolean containsAny(String blob, String... terms) {
        if (blob == null || blob.isEmpty() || terms == null) {
            return false;
        }
        for (String term : terms) {
            if (term != null && !term.isBlank() && blob.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static final Map<String, String[]> AI = Map.of(
            "Anthropic", terms("anthropic", "claude"),
            "OpenAI", terms("openai", "chatgpt", "gpt-6", "gpt 6", "gpt-5", "gpt 5"),
            "Grok", terms("grok", "xai", "x.ai"),
            "Google", terms("gemini", "gemma", "deepmind", "google ai"),
            "Meta", terms("meta ai", "llama", "muse image", "llama 4"),
            "Microsoft", terms("copilot", "windows ml", "microsoft 365 copilot")
    );

    private static final Map<String, String[]> IT = Map.of(
            "Semiconductors", terms(
                    "semiconductor", "nvidia", "amd", "intel", "tsmc", "gpu", "chip",
                    "accelerator", "foundry", "wafer", "asic", "silicon"),
            "Cybersecurity", terms(
                    "cybersecurity", "cyber security", "crowdstrike", "palo alto",
                    "zero trust", "ransomware", "malware", "firewall", "endpoint security",
                    "security breach", "infosec", "soc "),
            "Cloud", terms(
                    "aws", "azure", "google cloud", "gcp", "kubernetes", "serverless",
                    "cloud platform", "cloud infrastructure", "saas infrastructure",
                    "cloudflare", "oracle cloud")
    );

    private static final Map<String, String[]> MEDICAL = Map.ofEntries(
            Map.entry("Paediatrics", terms(
                    "paediatric", "pediatric", "paediatrics", "pediatrics",
                    "childhood", "neonatal", "neonate", "newborn", "infant", "toddler",
                    "child health", "kids health", "children's", "childrens", "nicu",
                    " for children", " in children", "child vaccine", "childhood vaccine")),
            Map.entry("Cardiology", terms(
                    "cardiology", "cardiac", "cardiologist", "heart failure", "heart attack",
                    "heart valve", "stent", "pacemaker", "ecg", "ekg", "defibrillator",
                    "arrhythmia", "angioplasty", "coronary")),
            Map.entry("Gynecology", terms(
                    "gynecology", "gynaecology", "gynecolog", "gynaecolog",
                    "fertility", "ivf", "endometriosis", "obstetric", "ob gyn", "ob/gyn",
                    "women's health", "womens health", "women health", "maternal", "menopause")),
            Map.entry("Oncology", terms(
                    "oncology", "oncolog", "cancer", "tumour", "tumor", "car-t", "car t",
                    "immunotherapy", "chemotherapy", "leukemia", "leukaemia",
                    "lymphoma", "melanoma")),
            Map.entry("Mental Health", terms(
                    "mental health", "depression", "anxiety", "psychiatry", "psychiatric",
                    "psychedelic", "antidepressant", "ptsd", "bipolar", "schizophrenia")),
            Map.entry("Dermatology", terms(
                    "dermatology", "dermatolog", "eczema", "psoriasis", "acne",
                    "skin disease", "skin treatment", "atopic")),
            Map.entry("Autism", terms(
                    "autism", "autistic", "asd ", " asd", "aba therapy", "neurodivers"))
    );

    private static final Map<String, String[]> SPACE = Map.of(
            "Satellites", terms(
                    "satellite", "starlink", "oneweb", "constellation", "earth observation",
                    "satellite broadband", "cubesat", "leo constellation"),
            "Launch", terms(
                    "falcon", "starship", "rocket", "lvm3", "launch vehicle", "reusable rocket",
                    "liftoff", "lift-off", "launch pad", "rocket lab", "isro launch"),
            "Commercial Space", terms(
                    "space tourism", "blue origin", "virgin galactic", "commercial crew",
                    "commercial space", "space station", "axiom space", "orbital reef"),
            "Lunar", terms(
                    "lunar", "artemis", "chandrayaan", "moon lander", "moon rover",
                    "moon mission", "lunar lander", "lunar rover", "to the moon")
    );

    private static final Map<String, String[]> SHARE_MARKET = Map.of(
            "IT", terms(
                    "tcs", "infosys", "wipro", "hcl tech", "tech mahindra", "nvidia",
                    "microsoft stock", "it stock", "software stock", "saas stock",
                    "it shares", "it earnings"),
            "EV", terms(
                    "tesla", "ev ", " ev", "electric vehicle", "ola electric", "tata motors",
                    "ev stock", "battery stock", "ev delivery", "ev shares", "ev maker"),
            "Space", terms(
                    "spacex", "satellite stock", "aerospace stock", "defence stock",
                    "defense stock", "rocket company", "space ipo", "space shares"),
            "Pharma", terms(
                    "pharma", "sun pharma", "dr reddy", "biotech", "fda", "drug approval",
                    "clinical trial", "pharma stock", "pharma shares", "cipla", "biocon"),
            "Banking", terms(
                    "hdfc bank", "icici", "sbi", "bank stock", "bank shares", "fintech",
                    "private bank", "banking stock", "axis bank", "kotak"),
            "Energy", terms(
                    "ongc", "reliance", "renewable", "solar", "wind energy", "oil gas",
                    "energy stock", "energy shares", "power stock", "adani green", "ntpc")
    );

    private static final Map<String, String[]> SOCIAL_MEDIA = Map.of(
            "Short Video", terms(
                    "reels", "tiktok", "youtube shorts", "short video", "shorts algorithm",
                    "reels monet", "snapchat spotlight"),
            "Social Commerce", terms(
                    "tiktok shop", "instagram shopping", "social commerce", "shoppable",
                    "in-app checkout", "meta shop", "live shopping"),
            "Messaging", terms(
                    "whatsapp", "imessage", "telegram", "signal", "messaging app",
                    "whatsapp channels", "rcs messaging", "chat app"),
            "Creators", terms(
                    "creator fund", "creator tools", "monetisation", "monetization",
                    "influencer", "newsletter", "creator economy", "creator platform")
    );

    private static final Map<String, String[]> GAMING = Map.of(
            "Mobile", terms(
                    "bgmi", "free fire", "pubg mobile", "mobile game", "genshin",
                    "honkai", "roblox", "mobile gaming", "android game", "ios game"),
            "Esports", terms(
                    "esports", "e-sports", "tournament", "prize pool", "valorant",
                    "dota", "franchise", "esports team", "gaming league"),
            "Console", terms(
                    "playstation", "xbox", "nintendo", "switch", "console", "ps5", "ps6",
                    "series x", "series s", "console exclusive"),
            "PC Gaming", terms(
                    "steam", "epic games", "pc game", "pc gaming", "gpu gaming",
                    "gaming laptop", "indie game", "aaa game"),
            "Game Dev", terms(
                    "unity", "unreal engine", "game engine", "game studio", "game sdk",
                    "game development", "game dev", "godot")
    );

    private static final Map<String, String[]> REAL_ESTATE = Map.of(
            "Residential", terms(
                    "flat", "apartment", "villa", "residential", "housing project",
                    "per sq ft", "psf", "home prices", "housing prices", "opens booking"),
            "Commercial", terms(
                    "commercial", "office", "warehouse", "mall", "reit", "leasing",
                    "office park", "retail space", "co-working", "coworking"),
            "PropTech", terms(
                    "proptech", "prop tech", "magicbricks", "99acres", "housing.com",
                    "brokerage", "listing platform", "property app", "buy offer"),
            "Construction", terms(
                    "construction", "prefab", "building material", "construction tech",
                    "cement", "infra project", "contractor"),
            "RERA", terms(
                    "rera", "real estate regulator", "housing regulator", "rera registration",
                    "rera clearance", "rera penalty")
    );

    private static final Map<String, String[]> ASTROLOGY = Map.of(
            "Horoscope", terms(
                    "horoscope", "zodiac", "sun sign", "moon sign", "daily horoscope",
                    "weekly horoscope"),
            "Vedic", terms(
                    "vedic", "jyotish", "kundli", "birth chart", "graha", "gochar",
                    "dasha", "planetary transit"),
            "Tarot", terms(
                    "tarot", "tarot card", "tarot reading", "tarot deck"),
            "Numerology", terms(
                    "numerology", "life path", "lucky number", "name numerology"),
            "Vastu", terms(
                    "vastu", "vastu shastra", "vastu tips", "vastu remedy")
    );

    private static String[] terms(String... values) {
        return values;
    }
}
