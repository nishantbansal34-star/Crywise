package com.nishant.crywise

import java.util.Calendar

data class Cause(val key: String, val title: String, val steps: List<String>, val workedIf: String)

data class Ranked(val cause: Cause, val score: Int, val reasons: List<String>)

/** What we know right now, mostly filled in automatically from the log. */
data class CryContext(
    val ageWeeks: Int?,
    val hoursSinceFeed: Double?,
    val awakeHours: Double?,      // 0.0 = baby was asleep and woke crying
    val hoursSinceNappy: Double?,
    val signs: Set<String>,
    val intensity: String?,       // "Mild", "Strong", "Very intense" or null
    val hour: Int,
)

data class Choice(val key: String, val label: String)

object Advisor {

    val SIGNS = listOf(
        Choice("root", "Rooting, sucking hands, smacking lips"),
        Choice("eyes", "Rubbing eyes, yawning, staring away"),
        Choice("legs", "Pulling legs up, hard tummy, red face"),
        Choice("arch", "Arching back during or after feeds"),
        Choice("spit", "Spitting up a lot"),
        Choice("drool", "Drooling, chewing on fists"),
        Choice("hot", "Sweaty neck, flushed, warm"),
        Choice("cold", "Cool chest or back of neck"),
        Choice("busy", "Busy day: noise, visitors, handling"),
        Choice("held", "Stops when picked up, starts when put down"),
        Choice("evening", "Same time each evening, hard to settle"),
        Choice("tug", "Pulling at ear, worse lying down"),
        Choice("vacc", "Had vaccines in the last 2 days"),
        Choice("poop", "No poo for a while, straining"),
    )

    val RED_FLAGS = listOf(
        Choice("fever3m", "Under 3 months with temperature 38°C or higher"),
        Choice("fever", "Fever 39°C+ at any age, or fever with a rash"),
        Choice("breath", "Fast, noisy or struggling breathing"),
        Choice("colour", "Blue, grey or very pale lips or skin"),
        Choice("floppy", "Floppy, very sleepy, hard to wake"),
        Choice("highcry", "Weak, high-pitched or very unusual cry"),
        Choice("nofeed", "Refusing feeds for several hours"),
        Choice("dry", "Fewer than 4 wet nappies in 24 hours"),
        Choice("vomit", "Green vomit or forceful vomiting"),
        Choice("fall", "Had a fall or bump to the head"),
        Choice("spot", "Soft spot on head bulging or sunken"),
    )

    val EMERGENCY = setOf("colour", "breath", "floppy", "fever3m", "spot")

    val CAUSES: Map<String, Cause> = listOf(
        Cause("hunger", "Hungry", listOf(
            "Offer a feed now, even if it hasn't been long. Growth spurts make babies want more.",
            "If bottle-feeding, check the teat flow isn't too slow."),
            "Feeds eagerly and settles during or after."),
        Cause("tired", "Overtired", listOf(
            "Dim the lights and cut the noise.",
            "Swaddle if under about 3 months and not rolling yet.",
            "Hold close, sway slowly, shush or play white noise.",
            "Put down drowsy, on the back, in a clear cot."),
            "Crying fades into slow breathing and heavy eyelids."),
        Cause("wind", "Wind or tummy discomfort", listOf(
            "Burp upright on your shoulder for a few minutes.",
            "On the back, do slow bicycle legs or a gentle clockwise tummy rub.",
            "Try holding tummy-down along your forearm."),
            "A burp or toot, then the body relaxes."),
        Cause("nappy", "Wet or dirty nappy", listOf(
            "Change the nappy and check for redness.",
            "Use barrier cream if the skin looks sore."),
            "Calms quickly once clean and dry."),
        Cause("temp", "Too hot or too cold", listOf(
            "Feel the chest or back of the neck, not the hands.",
            "Too warm: take off a layer. Cool: add one."),
            "Skin feels comfortably warm and baby settles."),
        Cause("stim", "Overstimulated", listOf(
            "Go to a quiet, dim room.",
            "Hold close with little talking, bouncing or eye contact.",
            "Swaddle or try skin-to-skin."),
            "Fewer jerky movements, crying slows."),
        Cause("comfort", "Wants to be held", listOf(
            "Pick up and hold close. You can't spoil a young baby.",
            "A carrier or sling keeps your hands free.",
            "A dummy can help if baby wants to suck."),
            "Settles in arms. That's a normal need."),
        Cause("pain", "Something hurting", listOf(
            "Check fingers, toes and private parts for a hair wrapped tightly around them.",
            "Look for tight clothes, scratchy labels, a pinching nappy or sore skin.",
            "Check temperature.",
            "If crying stays this intense and you can't find a cause, call your doctor."),
            "Crying drops sharply once the problem is removed."),
        Cause("colic", "Colic (evening crying)", listOf(
            "Rule out the other causes first.",
            "Swaddle, hold on the side in your arms, shush, sway, offer something to suck.",
            "A walk, a drive or a warm bath can reset baby.",
            "Take turns with another adult. See a doctor if it's over 3 hours most days."),
            "Comes around the same time daily and eases by 3–4 months."),
        Cause("reflux", "Reflux", listOf(
            "Keep upright for 20–30 minutes after feeds.",
            "Smaller, more frequent feeds; burp mid-feed.",
            "Tell your doctor if baby isn't gaining weight or seems in pain."),
            "Less fussing when kept upright after feeds."),
        Cause("teeth", "Teething", listOf(
            "Rub the gums with a clean finger.",
            "Offer a chilled (not frozen) teether.",
            "Ask your doctor before giving any pain medicine."),
            "Calmer while chewing on something cool."),
        Cause("ear", "Possible ear infection or illness", listOf(
            "Check temperature.",
            "See your doctor, especially with fever, a cold, or crying when lying flat."),
            "A doctor can confirm and treat it."),
        Cause("vacc", "After-vaccine soreness", listOf(
            "Extra cuddles and feeds.",
            "A cool cloth on the injection spot.",
            "Ask your doctor about medicine and call if fever is high or lasts over 2 days."),
            "Fussiness eases in a day or two."),
        Cause("constip", "Constipation", listOf(
            "Bicycle legs and tummy massage.",
            "A warm bath to relax the tummy.",
            "See your doctor if stools are hard pellets or there's blood."),
            "Passes a stool and relaxes."),
    ).associateBy { it.key }

    fun feedBucket(h: Double?) = when {
        h == null -> "?"; h < 1 -> "<1"; h < 2 -> "1-2"; h < 3 -> "2-3"; else -> "3+"
    }

    fun awakeBucket(h: Double?) = when {
        h == null -> "?"; h < 0.1 -> "just woke"; h < 1 -> "<1"; h < 2 -> "1-2"; else -> "2+"
    }

    fun contextFromLog(store: Store, signs: Set<String>, intensity: String?): CryContext {
        val now = System.currentTimeMillis()
        fun hoursSince(t: Long?) = t?.let { (now - it) / 3_600_000.0 }
        val lastWake = store.lastOf(EventType.WAKE)?.time
        val lastSleep = store.lastOf(EventType.SLEEP)?.time
        val awake = when {
            lastSleep != null && (lastWake == null || lastSleep > lastWake) -> 0.0
            lastWake != null -> hoursSince(lastWake)
            else -> null
        }
        return CryContext(
            ageWeeks = store.ageWeeks(now),
            hoursSinceFeed = hoursSince(store.lastOf(EventType.FEED)?.time),
            awakeHours = awake,
            hoursSinceNappy = hoursSince(store.lastOf(EventType.NAPPY)?.time),
            signs = signs,
            intensity = intensity,
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        )
    }

    fun rank(ctx: CryContext, history: List<Episode>, babyName: String): List<Ranked> {
        val score = CAUSES.keys.associateWith { 0 }.toMutableMap()
        val why = CAUSES.keys.associateWith { mutableListOf<String>() }
        fun add(k: String, n: Int, reason: String? = null) {
            score[k] = score.getValue(k) + n
            if (reason != null) why.getValue(k).add(reason)
        }

        val age = ctx.ageWeeks
        val group = when {
            age == null -> "?"; age < 6 -> "nb"; age < 13 -> "y"; age < 26 -> "m"; else -> "o"
        }
        val young = group == "nb" || group == "y" || group == "?"

        // Feeding gaps: newborns feed every 2–3 hours, older babies every 3–4
        when (val h = ctx.hoursSinceFeed) {
            null -> add("hunger", 1)
            else -> when {
                h >= 3 -> add("hunger", 4, "Last feed was ${fmtHours(h)} ago")
                h >= 2 -> add("hunger", if (young) 3 else 1, if (young) "Last feed ${fmtHours(h)} ago, a normal gap at this age" else null)
                h >= 1 -> { if (group == "nb") add("hunger", 1) }
                else -> { add("wind", 1, "Fed under an hour ago, so trapped air is common"); add("hunger", -2) }
            }
        }

        // Typical awake windows (hours) by age
        val ww = when (group) { "nb" -> 0.75 to 1.0; "y" -> 1.0 to 1.5; "m" -> 1.5 to 2.5; "o" -> 2.0 to 3.5; else -> 1.0 to 2.0 }
        ctx.awakeHours?.let { h ->
            when {
                h < 0.1 -> add("tired", -1)
                h >= ww.second -> add("tired", 4, "Awake ${fmtHours(h)}, longer than usual for this age")
                h >= ww.first -> add("tired", 2, "Awake ${fmtHours(h)}, near the end of a typical awake stretch")
                else -> {}
            }
        }

        when (val h = ctx.hoursSinceNappy) {
            null -> add("nappy", 1)
            else -> when {
                h >= 2 -> add("nappy", 2, "Nappy last changed ${fmtHours(h)} ago")
                h >= 1 -> add("nappy", 1)
                h < 0.25 -> add("nappy", -3)
                else -> {}
            }
        }

        when (ctx.intensity) {
            "Very intense" -> { add("pain", 3, "The cry sounded very intense and piercing"); add("wind", 1) }
            "Strong" -> add("pain", 1)
            else -> {}
        }

        val s = ctx.signs
        if ("root" in s) add("hunger", 4, "Rooting and sucking are hunger cues")
        if ("eyes" in s) add("tired", 4, "Eye-rubbing and yawning are sleep cues")
        if ("legs" in s) { add("wind", 4, "Pulled-up legs and a hard tummy point to wind"); add("colic", 1); add("constip", 1) }
        if ("arch" in s) { add("reflux", 4, "Arching around feeds is a common reflux sign"); add("wind", 1) }
        if ("spit" in s) { add("reflux", 2, "Spitting up a lot"); add("wind", 1) }
        if ("drool" in s) { if (group == "m" || group == "o") add("teeth", 4, "Drooling and chewing at teething age") else add("hunger", 1) }
        if ("hot" in s) add("temp", 4, "Sweaty or flushed suggests too warm")
        if ("cold" in s) add("temp", 4, "A cool chest suggests too cold")
        if ("busy" in s) { add("stim", 4, "A busy day can overload a small baby"); add("tired", 1) }
        if ("held" in s) add("comfort", 4, "Settles in arms")
        if ("evening" in s) {
            if (group == "nb" || group == "y") add("colic", 4, "Evening crying at 2 weeks to 3 months fits colic")
            else add("tired", 2, "Evening fussiness is often tiredness")
            add("stim", 1)
        }
        if ("tug" in s) add("ear", 4, "Ear-pulling and worse lying down")
        if ("vacc" in s) add("vacc", 5, "Recent vaccines often cause a fussy day")
        if ("poop" in s) add("constip", 4, "Straining with no recent poo")

        if (group == "m" || group == "o") add("colic", -2)
        if (group != "m" && group != "o") add("teeth", -3)

        // Learn from this baby's own history: what settled them in similar moments
        val fb = feedBucket(ctx.hoursSinceFeed)
        val ab = awakeBucket(ctx.awakeHours)
        val name = babyName.ifBlank { "your baby" }
        history.filter { it.worked != null && CAUSES.containsKey(it.worked) }
            .groupBy { it.worked!! }
            .forEach { (cause, eps) ->
                val similar = eps.count { e ->
                    (fb != "?" && e.feedBucket == fb) || (ab != "?" && e.awakeBucket == ab) ||
                        hourDistance(e.hour, ctx.hour) <= 1
                }
                if (similar > 0) {
                    add(cause, minOf(4, similar + 1),
                        "This worked for $name ${times(similar)} in a similar situation")
                }
            }

        return score.filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .map { (k, v) -> Ranked(CAUSES.getValue(k), v, why.getValue(k)) }
            .ifEmpty {
                listOf(Ranked(CAUSES.getValue("comfort"), 1,
                    listOf("Nothing else stands out. Sometimes babies just need closeness")))
            }
    }

    private fun hourDistance(a: Int, b: Int): Int { val d = kotlin.math.abs(a - b); return minOf(d, 24 - d) }
    private fun times(n: Int) = if (n == 1) "once" else "$n times"
}

fun fmtHours(h: Double): String {
    val mins = (h * 60).toInt()
    return when {
        mins < 60 -> "$mins min"
        mins % 60 == 0 -> "${mins / 60} hr"
        else -> "${mins / 60} hr ${mins % 60} min"
    }
}
