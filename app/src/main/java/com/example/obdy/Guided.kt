package com.example.obdy

/** A follow-up question written by the AI. */
class AiQuestion(val q: String, val options: List<String>)

/** A question with answer choices. [multi] = more than one answer allowed. */
class Question(val id: String, val text: String, val options: List<String>, val multi: Boolean)

object Guided {
    /** Questions every code gets, plus a few for the family of the code. [causes] fills the "what have you already done" question. */
    fun questions(codeIn: String): List<Question> {
        val c = codeIn.take(5).uppercase()
        val done = DtcDb.causes(c).map { it.take(70) }
        val q = ArrayList<Question>()
        q.add(Question("done", "What have you already replaced, cleaned or checked for this code?", done + listOf("Something else (write below)", "Nothing yet"), true))
        q.add(Question("when", "When does it happen?", listOf("Always", "Only when cold", "Only when hot", "While accelerating", "At idle or stopped", "Randomly", "I do not know"), false))
        q.add(Question("feel", "What do you notice in the car?", listOf("Rough or shaking idle", "Loss of power", "Hard starting", "Stalling", "Poor fuel economy", "Harsh or slipping shifts", "Strange noise or smell", "Only the warning light"), true))
        q.add(Question("after", "Did it start after something?", listOf("Refuelling", "Rain or car wash", "A repair or part change", "Battery change or disconnect", "A long hot drive", "Nothing I can think of"), false))
        q.add(Question("back", "If you cleared it, how soon did it return?", listOf("Straight away", "Within a day", "Within a week", "I never cleared it"), false))
        when {
            Regex("^P030[0-8]$").matches(c) -> {
                q.add(Question("flash", "Is the warning light flashing?", listOf("Yes, flashing", "No, steady", "It is off"), false))
                q.add(Question("misfire", "Misfire pattern", listOf("One cylinder only", "Moves between cylinders", "I do not know"), false))
            }
            c == "P0171" || c == "P0174" || c == "P0172" || c == "P0175" -> {
                q.add(Question("hiss", "Do you hear hissing or whistling from the engine at idle?", listOf("Yes", "No", "Not sure"), false))
                q.add(Question("filter", "When was the air filter last changed?", listOf("Less than 6 months", "6 to 12 months", "More than a year", "Never / do not know"), false))
            }
            c.startsWith("P044") || c.startsWith("P045") -> {
                q.add(Question("cap", "Does the fuel cap click when tightened, and does the seal look cracked?", listOf("Clicks and looks good", "Clicks but seal is worn", "No click / loose", "Not checked"), false))
                q.add(Question("fuel", "Did it appear right after filling the tank?", listOf("Yes", "No"), false))
            }
            c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") -> {
                q.add(Question("fluid", "Has the automatic transmission fluid been changed?", listOf("Less than 60,000 km ago", "More than 60,000 km ago", "Never", "I do not know"), false))
                q.add(Question("shift", "How do the shifts feel?", listOf("Harsh bang", "Slipping or flaring rpm", "Stuck in one gear", "Normal"), false))
            }
            c.startsWith("U0") -> {
                q.add(Question("batt", "How old is the battery, and does the car crank slowly?", listOf("Under 2 years, starts fast", "2 to 3 years", "Over 3 years", "Slow cranking"), false))
                q.add(Question("lights", "Several warning lights at once?", listOf("Yes, many lights", "Only one or two", "No"), false))
            }
            c.startsWith("C0") -> q.add(Question("abs", "Which lights are on?", listOf("ABS", "Traction / StabiliTrak", "Brake", "More than one"), true))
            c.startsWith("B0") -> q.add(Question("seatq", "Seat belt or seat recently touched (cleaning, child seat, heavy item)?", listOf("Yes", "No"), false))
        }
        return q
    }

    /** Offline "next best" ideas: the usual causes minus what was already done, plus the jobs that fix a code after the obvious part did not. */
    fun nextSteps(codeIn: String, replaced: Set<String>): List<String> {
        val c = codeIn.take(5).uppercase()
        val out = ArrayList<String>()
        val doneStarts = replaced.map { it.take(40) }
        for (cause in DtcDb.causes(c)) if (doneStarts.none { cause.startsWith(it) }) out.add(cause)
        val extra = when {
            Regex("^P030[0-8]$").matches(c) || c == "P0300" -> listOf("Clean the throttle body and intake (carbon).", "Check the coil connector and wiring of the cylinder, not only the coil.", "Check fuel quality: fill with fresh fuel from a busy station.", "Do a compression test if plugs and coils are new.")
            c == "P0171" || c == "P0174" -> listOf("Replace or clean the air filter, then check the intake tube clamps and the crankcase (PCV) hoses.", "Clean the MAF sensor (do not replace first).", "Smoke-test for vacuum leaks.", "Check fuel pressure and the fuel filter.")
            c == "P0172" || c == "P0175" -> listOf("Check the air filter is not clogged.", "Check the purge valve is not stuck open (rough idle after refuelling).", "Check fuel pressure is not too high.")
            c == "P0420" || c == "P0430" -> listOf("Check for exhaust leaks before the sensors.", "Check upstream and downstream O2 sensor readings in Live data.", "Fix any misfire first: it can damage the catalyst.")
            c.startsWith("P044") || c.startsWith("P045") -> listOf("Replace the fuel cap with a good quality one, clear the code and drive 3 days.", "Inspect the filler neck and the hoses near the canister.", "Test the vent valve and purge valve with a hand vacuum pump.")
            c.startsWith("P07") -> listOf("Check the fluid level and condition at a workshop.", "Check the connector at the transmission for water or oil.", "Update the transmission software at a dealer if available.")
            c.startsWith("U0") -> listOf("Test the battery and charging (Tools > Battery test).", "Clean and tighten battery and ground points.", "Check for recently added accessories (dash cam, alarm, amplifier).")
            c.startsWith("C0") -> listOf("Clean metal dust from the wheel sensor tip and check its wire.", "Check the wheel bearing for play.", "Check the battery voltage.")
            else -> emptyList()
        }
        for (e in extra) if (e !in out) out.add(e)
        return out
    }
}
