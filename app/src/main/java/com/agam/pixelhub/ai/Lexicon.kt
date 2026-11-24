package com.agam.pixelhub.ai

import com.agam.pixelhub.data.ExpenseCategory
import java.util.Locale

/**
 * Every word the assistant understands, English and Hinglish, in one place. To teach it a new
 * word, add it here. Everything is matched lower-case on whole words, after [normalize].
 */
internal object Lexicon {

    /**
     * Hinglish and shorthand rewritten to the English the rules expect, so "kal subah 7 baje"
     * reads as "kal morning 7 oclock". Applied in order, so longer phrases come first.
     * ("kal" and "parso" stay as they are: they mean tomorrow for a reminder but yesterday
     * for an expense.)
     */
    private val SYNONYMS: List<Pair<Regex, String>> = listOf(
        "har roz|har din|roz|rozana|roj|rojana|everyday|each day" to "every day",
        "har hafte|har hafta|weekly" to "every week",
        "har" to "every",
        "subah|subha|savere|sawere" to "morning",
        "dopahar|dopehar|dophar|dupahar" to "afternoon",
        // Not "sham": it's a common name, and "call Sham" became "call evening".
        "shaam" to "evening",
        "raat|raath" to "night",
        "aaj" to "today",
        "tmrw|tmrrw|tmr|tmw|tomm|tommorow|tomorow|tommorrow|tomorrw" to "tomorrow",
        "tonite" to "tonight",
        "baje|bajey|bajay|bje|o'clock|o clock" to "oclock",
        "ghante|ghanta|ghanton|hrs|hr" to "hours",
        // Not "mint": "buy mint" became "Buy minutes".
        "mins|minat|minute" to "minutes",
        "hafte|hafta|hafton" to "weeks",
        "din" to "days",
        "agle|agla|agli" to "next",
        "baad mein|baad me|ke baad|baad|bad mein|bad me" to "later",
        "somvar|somwar" to "monday",
        "mangalvar|mangalwar" to "tuesday",
        "budhvar|budhwar" to "wednesday",
        "guruvar|guruwar|veervar|virvar|brihaspativar" to "thursday",
        "shukravar|shukrawar" to "friday",
        "shanivar|shaniwar" to "saturday",
        "ravivar|raviwar|itvar|itwar|etvar" to "sunday",
    ).map { (from, to) -> Regex("""\b(?:$from)\b""") to to }

    /**
     * Lower-case, ₹ → "rs", "1,20,000" → "120000", punctuation spaced out, synonyms applied unless
     * [synonyms] is false (for names, places and search queries: "har ki pauri" isn't "every ki pauri").
     */
    fun normalize(raw: String, synonyms: Boolean = true): String {
        var s = raw.lowercase(Locale.ROOT)
            .replace('’', '\'').replace('‘', '\'')
            .replace("₹", " rs ")
        s = Regex("""(?<=\d),(?=\d)""").replace(s, "")
        s = s.replace(",", " , ")
        s = Regex("""[!?;"“”()\[\]]""").replace(s, " ")
        s = Regex("""\.(?=\s|$)""").replace(s, " ")
        s = Regex("""\s+""").replace(s, " ").trim()
        if (synonyms) for ((re, to) in SYNONYMS) s = re.replace(s, to)
        return s
    }

    // ---- What kind of request it is -------------------------------------------------------

    val HELP = Regex("""^(?:help|commands|what can you do|how does this work|madad|kya kar sakte ho)$""")

    /** Starts a reminder. Consumed with the words around it ("remind me to", "yaad dila do"). */
    val REMINDER = Regex(
        """\b(?:(?:please|pls|plz)\s+)?(?:set\s+(?:a\s+)?)?(?:""" +
            """remind(?:er)?s?(?:\s+me)?(?:\s+(?:to|for|about|that))?""" +
            """|remember\s+to|don'?t\s+let\s+me\s+forget(?:\s+to)?|note\s+to\s+self""" +
            """|yaad\s+(?:dila|kara)\w*(?:\s+(?:do|dena|dijiye|na))?""" +
            """)\b""",
    )

    /**
     * Starts an alarm ("wake me up", "alarm laga do", "utha dena"). It leaves "at" and "for" in
     * place: taking them left a bare "7" in "alarm at 7", which no time rule reads.
     */
    val ALARM = Regex(
        """\b(?:(?:please|pls|plz)\s+)?(?:""" +
            """wake\s+me\s+up|wake\s+me|wake\s+up|set\s+(?:an?\s+|the\s+)?alarm|alarm""" +
            """|uthaa?(?:na|o|do|dena)?|jaga(?:na|o|do|dena)?""" +
            """)\b""",
    )

    /** Money coming in. "got" counts only right before an amount ("got 500 from dad"). */
    val INCOME = Regex(
        """\b(?:got\s+paid|get\s+paid|received|recieved|receive|earned|earn|credited|salary|stipend""" +
            """|refund(?:ed)?|cashback|income|bonus|dividend|interest|tankhwah|tankha""" +
            """|mil[aei]|aay?[aei]|kamaye?|kamai)\b|\bgot(?=\s+(?:rs\.?\s*)?\d)""",
    )

    /** Money going out. Only used to tell that a message is about money. */
    val EXPENSE = Regex(
        """\b(?:spent|spend|spending|paid|pay|payed|bought|buy|purchased?|cost|costs|gave|given""" +
            """|add|added|adding|daal\s+do|daalo|daal\s+dena|jod\s+do|jodo|jod\s+dena""" +
            """|kharch\w*|kharid\w*|diye|diya|lage|lagaye|liya|expenses?|bill)\b""",
    )

    /**
     * Words that say the money went out, strongly enough to beat an income category: "spent 1500
     * on birthday gift" is spending, though "birthday" is a Gifts (income) word. Not "add" ("add
     * 500 to salary" is income), and not "paid" after got/was or before me/us ("got paid",
     * "client paid me 5000"), which is money coming in.
     */
    val SPENT = Regex(
        """\b(?:spent|spend|spending|(?<!\b(?:got|get|was|were|been)\s)(?:paid|pay|payed)(?!\s+(?:me|us|mujhe)\b)""" +
            """|bought|buy|purchased?|cost|costs|kharch\w*|kharid\w*|bills?)\b""",
    )

    /** The verbs around an amount, removed so the note keeps only what it was for. */
    val MONEY_VERBS = Regex(
        """\b(?:got\s+paid|get\s+paid|received|recieved|receive|earned|earn|credited|got|income|expenses?""" +
            """|add(?:ed|ing)?(?:\s+(?:karo|kar\s+do|kardo|kar\s+dena|kar))?|daal\s+do|daalo|daal\s+dena|jod\s+do|jodo|jod\s+dena""" +
            """|spent|spend|spending|paid|pay|payed|bought|buy|purchased?|cost|costs|gave|given""" +
            """|kharch\w*|kharid\w*|diye|diya|de\s+diye|de\s+diya|lage|lagaye|liya|kiye|kiya|kar\s+diye""" +
            """|mil[aei]|aay?[aei]|kamaye?|kamai|hue|huye|ho\s+gaye)\b""",
    )

    // ---- People ledger (who owes whom) ---------------------------------------------------

    /** Ledger words removed from the note ("paid naruna 30 for lunch" → "lunch"). */
    val LEDGER_WORDS = Regex(
        """\b(?:paid|gave|lent|lend|sent|transferred|owes|owe|borrowed|got|received|recieved|spent""" +
            """|de\s+diye|de\s+diya|diye|diya|udhaar|udhar|le\s+liye|liye|liya|mile|mila|mili|aaye|aaya""" +
            """|bheje|bheja|pay\s+kiye|kiye|kiya)\b""",
    )

    val LEDGER_FILLERS = setOf(
        "i", "me", "mujhe", "mere", "to", "for", "from", "ko", "se", "ne", "ke", "ka", "ki", "liye", "on", "at", "the",
        "a", "an", "rs", "rupees", "of", "in", "mein", "pe", "par", "and", "back", "wapas", "hai", "h", "tha", "my",
    )

    /** Words that are never a person's name, so "paid rent 500" or "paid swiggy 300" stays an expense. */
    val NOT_A_NAME: Set<String> by lazy {
        setOf(
            "rs", "inr", "rupees", "me", "him", "her", "them", "you", "someone", "somebody", "it", "back", "bill", "bills",
            "fees", "fee", "emi", "tax", "cash", "money", "paisa", "paise", "mera", "meri", "mere", "my", "myself",
            "everyone", "all", "total", "amount", "upi", "online", "card", "advance", "deposit", "kharcha", "the",
            "today", "tomorrow", "yesterday", "kal", "parso", "month", "week", "mahine", "aaj", "sab", "pura",
            // Pronouns and words that hedge an amount: "he owes me 50", "paid only 200 for lunch".
            "he", "she", "they", "we", "us", "only", "just", "around", "about", "approx", "approximately", "extra",
            "another", "off", "almost", "nearly", "over", "again", "more", "less", "upto", "full", "half",
        ) + CATEGORY_WORDS.values.flatten() + MONEY_FILLERS + REMINDER_FILLERS
    }

    // ---- Opening things -----------------------------------------------------------------

    val OPEN = Regex("""^(?:(?:please|pls)\s+)?(?:open|launch|start|run|go\s+to)\s+(?:the\s+|my\s+)?(.+?)(?:\s+app)?$""")
    val OPEN_HI = Regex(
        """^(.+?)(?:\s+app)?\s+(?:kholo|khol\s+do|khol\s+de|kholna|khol|chalao|chala\s+do|chalu\s+karo""" +
            """|open\s*(?:karo|kar\s+do|kardo|kar)?|start\s+karo)$""",
    )

    val CALL = Regex("""^(?:(?:please|pls)\s+)?(?:call|dial|phone|ring)\s+(?:up\s+|to\s+)?(.+)$""")
    val CALL_HI = Regex("""^(.+?)\s+(?:ko\s+|pe\s+|par\s+)?(?:call|phone|fone)\s*(?:karo|kar\s+do|kar|lagao|laga\s+do|lagana|milao)$""")
    /**
     * A phone number, in groups of any size ("98765 43210", "98 76 54 32 10", "+44 20 7946 0958").
     * It can run on into a message that starts with a number; CommandParser's splitNumber puts
     * the "5" of "text 9876543210 5 min late" back in the message.
     */
    const val PHONE_NUM = """\+?\d(?:[\d-]|\s(?=\d))*\d"""
    val PHONE = Regex(PHONE_NUM)
    val SMS = Regex("""^(?:text|sms|message|msg)\s+(?:to\s+)?($PHONE_NUM)(?:\s+(?:saying\s+|that\s+)?(.+))?$""")
    val NAVIGATE = Regex("""^(?:(?:please|pls)\s+)?(?:navigate|directions?|take\s+me|drive|route)\s+(?:to\s+|for\s+)?(.+)$""")
    val NAVIGATE_HI = Regex(
        """^(.+?)\s+(?:ka|ki|ke)\s+(?:rasta|raasta|route|directions?)(?:\s+(?:dikhao|batao|do|dikha\s+do))?$""" +
            """|^(.+?)\s+(?:le\s+chalo|jaana\s+hai|jana\s+hai)$""",
    )
    val SEARCH = Regex("""^(?:(?:please|pls)\s+)?(?:search(?:\s+for)?|google|look\s+up)\s+(.+)$""")
    val SEARCH_HI = Regex("""^(.+?)\s+(?:search|google|dhundo|dhoondo|khojo)\s*(?:karo|kar\s+do|kar)?$""")
    val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    val MAIL_WORD = Regex("""\b(?:e-?mail|mail)\b""")
    /**
     * A link on its own, matched against the raw text: normalising lower-cases paths and drops the
     * "?" of a query string. A full stop or comma at the very end isn't part of it.
     */
    val URL_ONLY = Regex(
        """^\s*(?:(?:please|pls)\s+)?(?:(?:open|go\s+to|visit|browse|launch)\s+)?((?:https?://)?(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*""" +
            """\.(?:com|in|org|net|io|co|dev|app|ai|me|gov|edu|info)(?:\.[a-z]{2})?(?:/\S*?)?)[.,]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // ---- Money categories ---------------------------------------------------------------

    /** Keywords per category. The keyword that appears first in the message wins. */
    val CATEGORY_WORDS: Map<ExpenseCategory, List<String>> = mapOf(
        ExpenseCategory.FOOD to listOf(
            "lunch", "dinner", "breakfast", "brunch", "snack", "snacks", "coffee", "tea", "chai", "food", "restaurant",
            "cafe", "zomato", "swiggy", "pizza", "burger", "grocery", "groceries", "blinkit", "zepto", "bigbasket",
            "instamart", "milk", "doodh", "sabzi", "vegetables", "fruits", "khana", "nashta", "juice", "biryani",
            "dominos", "mcdonalds", "kfc", "starbucks", "canteen", "mess", "tiffin", "maggi", "ration",
        ),
        ExpenseCategory.TRANSPORT to listOf(
            "auto", "uber", "ola", "rapido", "cab", "taxi", "metro", "bus", "petrol", "diesel", "fuel", "cng",
            "parking", "toll", "fastag", "rickshaw", "ride",
        ),
        ExpenseCategory.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "clothes", "shirt", "jeans", "shoes", "kapde",
            "shopping", "mall", "headphones", "gadget",
        ),
        ExpenseCategory.BILLS to listOf(
            "rent", "kiraya", "electricity", "bijli", "wifi", "internet", "broadband", "recharge", "bill", "emi",
            "gas", "cylinder", "dth", "maintenance", "insurance",
        ),
        ExpenseCategory.HEALTH to listOf(
            "medicine", "medicines", "dawai", "dawa", "doctor", "pharmacy", "chemist", "hospital", "clinic", "gym",
            "checkup", "dentist",
        ),
        ExpenseCategory.ENTERTAINMENT to listOf(
            "movie", "movies", "film", "netflix", "prime", "hotstar", "spotify", "game", "games", "concert", "party",
            "pvr", "inox", "bookmyshow",
        ),
        ExpenseCategory.TRAVEL to listOf(
            "train", "flight", "hotel", "trip", "irctc", "makemytrip", "goibibo", "airbnb", "oyo", "holiday", "vacation",
        ),
        ExpenseCategory.SALARY to listOf("salary", "stipend", "payroll", "bonus", "tankhwah", "tankha"),
        ExpenseCategory.FREELANCE to listOf("freelance", "client", "project", "gig", "invoice"),
        ExpenseCategory.GIFTS to listOf("gift", "gifts", "birthday", "shagun"),
        // Not "return": "return flight 5000" is travel spending, not a refund.
        ExpenseCategory.REFUNDS to listOf("refund", "refunded", "cashback"),
        ExpenseCategory.INVESTMENTS to listOf("dividend", "interest", "sip", "mutual", "stocks", "shares", "fd"),
    )

    // ---- Filler words dropped from the ends of a title, label or note --------------------

    val REMINDER_FILLERS = setOf(
        "to", "that", "about", "for", "at", "on", "by", "me", "mujhe", "muje", "mujhko", "ki", "ko", "ke", "liye", "se",
        // No "set": REMINDER already takes "set a reminder", and as a filler it ate the task in
        // "remind me to set the table" (leaving "Table").
        "please", "pls", "plz", "hai", "hain", "h", "karna", "karo", "kar", "dena", "de", "na", "the", "a", "an",
        "and", "is", "of", "i", "oclock", "later", "now", "abhi", "bhi", "wala", "wali", "in",
    )
    val ALARM_FILLERS = setOf(
        "to", "for", "at", "on", "me", "mujhe", "set", "laga", "lagao", "lagana", "lagado", "do", "dena", "de", "karo",
        "kar", "please", "pls", "the", "a", "an", "ka", "ke", "liye", "ko", "ki", "hai", "se", "up", "alarm", "oclock", "my",
        "wala", "in", "and",
    )
    val MONEY_FILLERS = setOf(
        "on", "for", "at", "to", "from", "in", "of", "the", "a", "an", "ka", "ki", "ke", "ko", "pe", "par", "mein", "me",
        "se", "liye", "ne", "maine", "mene", "i", "my", "rs", "rupees", "total", "worth", "hai", "h", "tha", "and", "was",
    )
}
