package search

import org.apache.commons.text.StringEscapeUtils
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Compiles search queries to [Query] objects
 *
 * A search query consists of one or more of the following elements:
 *
 * **Term**
 *
 * A string that should appear somewhere in the document to find
 *
 * **Date**
 *
 * A string in the form `yyyy-MM-dd` (e.g. `2022-05-20`)
 *
 * **Date time**
 *
 * A string in the form `yyyy-MM-dd'T'HH:mm[:ss]` (e.g. `2022-05-20T16:36` or
 * `2022-05-20T16:36:12`
 *
 * **Locator**
 *
 * A string starting with `in:` and denoting the attribute that should be
 * compared with the give term(s). See `Attributes` below for a complete list
 * of all possible attributes. Example: `in:name`
 *
 * **Type**
 *
 * A string starting with `is:` and denoting the type of documents to search.
 * Possible values are `is:workflow` and `is:processchain`.
 *
 * **Filter**
 *
 * A string that starts with an attribute name and gives a term that should
 * appear in this attribute. See `Attributes` below for a complete list
 * of all possible attributes. Example: `name:Elvis`
 *
 * **Attributes**
 *
 * Possible values for attributes are:
 *
 * * `id`
 * * `name`
 * * `error`, `errormessage`
 * * `rc`, `cap`, `reqcap`, `capability`, `requiredcapability`,
 *   `rcs`, `caps`, `reqcaps`, `capabilities`, `requiredcapabilities`
 *
 * @author Michel Kraemer
 */
object QueryCompiler {
  private val DATE_REGEX = """(\d{4})-(\d{2})-(\d{2})""".toRegex()
  private val DATETIME_REGEX = """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(:(\d{2}))?""".toRegex()

  // attributes
  private const val ID = "id"
  private const val NAME = "name"
  private val ERROR = listOf("error", "errormessage")
  private val RCS = listOf(
      "rc", "cap", "reqcap", "capability", "requiredcapability",
      "rcs", "caps", "reqcaps", "capabilities", "requiredcapabilities"
  )
  private val ALL_ATTRIBUTES = listOf(ID, NAME) + ERROR + RCS

  // types
  private const val WORKFLOW = "workflow"
  private const val PROCESSCHAIN = "processchain"

  /**
   * Compile the given [query]
   */
  fun compile(query: String): Query {
    val parts = QuotedStringSplitter.split(query)

    val terms = mutableSetOf<Term>()
    val filters = mutableSetOf<Pair<Locator, Term>>()
    val locators = mutableSetOf<Locator>()
    val types = mutableSetOf<Type>()

    for (p in parts) {
      if (p.second) {
        // string was quoted - take it as is
        terms.add(StringTerm(p.first))
      } else {
        try {
          val colon = p.first.indexOf(":")
          if (colon > 0) {
            val key = p.first.substring(0, colon)
            val value = unquote(p.first.substring(colon + 1))
            when (key.lowercase()) {
              "in" -> locators.add(parseLocator(value))
              "is" -> types.add(parseType(value))
              in ALL_ATTRIBUTES -> filters.add(parseLocator(key) to parseTerm(value))
              else -> throw IllegalStateException()
            }
          } else {
            throw IllegalStateException()
          }
        } catch (e: IllegalStateException) {
          terms.add(parseTerm(p.first))
        }
      }
    }

    return Query(terms = terms, filters = filters, locators = locators, types = types)
  }

  private fun unquote(str: String): String {
    return if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
      StringEscapeUtils.unescapeJava(str.substring(1, str.length - 1))
    } else {
      str
    }
  }

  private fun parseTerm(term: String): Term {
    return DATE_REGEX.matchEntire(term)?.let { dateMatch ->
      DateTerm(LocalDate.of(
          dateMatch.groupValues[1].toInt(),
          dateMatch.groupValues[2].toInt(),
          dateMatch.groupValues[3].toInt()
      ))
    } ?: DATETIME_REGEX.matchEntire(term)?.let { dateTimeMatch ->
      DateTimeTerm(LocalDateTime.of(
          dateTimeMatch.groupValues[1].toInt(),
          dateTimeMatch.groupValues[2].toInt(),
          dateTimeMatch.groupValues[3].toInt(),
          dateTimeMatch.groupValues[4].toInt(),
          dateTimeMatch.groupValues[5].toInt(),
          if (dateTimeMatch.groupValues[7].isNotBlank()) dateTimeMatch.groupValues[7].toInt() else 0
      ))
    } ?: run {
      StringTerm(term)
    }
  }

  private fun parseLocator(str: String): Locator {
    return when (str.lowercase()) {
      ID -> Locator.ID
      NAME -> Locator.NAME
      in ERROR -> Locator.ERROR_MESSAGE
      in RCS -> Locator.REQUIRED_CAPABILITIES
      else -> throw IllegalStateException()
    }
  }

  private fun parseType(str: String): Type {
    return when (str.lowercase()) {
      WORKFLOW -> Type.WORKFLOW
      PROCESSCHAIN -> Type.PROCESS_CHAIN
      else -> throw IllegalStateException()
    }
  }
}