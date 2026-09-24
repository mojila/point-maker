/**
 * Jev (System One) decision tool for OpenCode — portable, dependency-free.
 *
 * Wraps kenari's POST /v1/systemone endpoint, which serves the typed decision
 * model `jev-1-13-free` (TypeSafe Jev 1.13). This is NOT a chat model: it takes
 * a state plus named typed questions and returns typed answers with
 * probabilities instead of prose.
 *
 * This file intentionally does not import `@opencode/plugin`, so it loads
 * without any npm install. It exports a plain plugin object matching the V2
 * promise-plugin shape: { id, setup(ctx) }.
 *
 * Requires KENARI_API_KEY (a `kn-...` key) in the environment, or an apiKey
 * passed through the plugin options in opencode.json:
 *   { "package": "./.opencode/plugins/jev-decision.ts",
 *     "options": { "apiKey": "kn-..." } }
 */

const ENDPOINT = "https://kenari.id/v1/systemone"
const MODEL = "jev-1-13-free"
const REQUEST_TIMEOUT_MS = 60000
const MAX_STATE_CHARS = 24000
const MAX_QUESTIONS = 32
const MAX_CHOICES = 255

class JevError extends Error {}

function assertQuestion(name, q) {
  if (!q || typeof q !== "object") {
    throw new JevError(`Question "${name}" must be an object.`)
  }
  if (typeof q.instructions !== "string" || q.instructions.trim() === "") {
    throw new JevError(`Question "${name}" needs non-empty "instructions".`)
  }
  if (q.type === "noul") {
    if (q.criteria !== undefined) {
      const c = q.criteria
      if (
        typeof c !== "object" ||
        Array.isArray(c) ||
        typeof c["true"] !== "string" ||
        typeof c["false"] !== "string"
      ) {
        throw new JevError(`Noul question "${name}" criteria must be an object with "true" and "false" strings.`)
      }
    }
    return
  }
  if (q.type === "choice") {
    const c = q.criteria
    if (!c || typeof c !== "object" || Array.isArray(c)) {
      throw new JevError(`Choice question "${name}" requires a criteria object mapping option -> description.`)
    }
    const options = Object.keys(c)
    if (options.length < 2) {
      throw new JevError(`Choice question "${name}" needs at least 2 options.`)
    }
    if (options.length > MAX_CHOICES) {
      throw new JevError(`Choice question "${name}" has ${options.length} options; the cap is ${MAX_CHOICES}.`)
    }
    for (const [key, value] of Object.entries(c)) {
      if (typeof value !== "string") {
        throw new JevError(`Choice question "${name}" option "${key}" must be a string description.`)
      }
    }
    return
  }
  if (q.type === "score") {
    const c = q.criteria
    if (!Array.isArray(c)) {
      throw new JevError(`Score question "${name}" requires criteria as an ordered low-to-high array.`)
    }
    if (c.length < 2 || c.length > 10) {
      throw new JevError(`Score question "${name}" needs 2 to 10 ordered levels, got ${c.length}.`)
    }
    for (const level of c) {
      if (typeof level !== "string" || level.trim() === "") {
        throw new JevError(`Score question "${name}" levels must be non-empty strings.`)
      }
    }
    return
  }
  throw new JevError(
    `Question "${name}" has unsupported type "${q.type}". Use noul, choice, or score.`,
  )
}

function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new JevError("Cancelled."))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal.addEventListener(
      "abort",
      () => {
        clearTimeout(timer)
        reject(new JevError("Cancelled."))
      },
      { once: true },
    )
  })
}

async function callSystemOne(apiKey, state, questions, signal) {
  const body = JSON.stringify({ model: MODEL, state, questions })

  for (let attempt = 0; attempt < 3; attempt++) {
    const timeout = AbortSignal.timeout(REQUEST_TIMEOUT_MS)
    const combined = AbortSignal.any([signal, timeout])

    let response
    try {
      response = await fetch(ENDPOINT, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiKey}`,
          "Content-Type": "application/json",
        },
        body,
        signal: combined,
      })
    } catch (error) {
      if (signal.aborted) throw new JevError("Cancelled.")
      if (timeout.aborted) throw new JevError(`Jev request timed out after ${REQUEST_TIMEOUT_MS / 1000}s.`)
      throw new JevError(`Jev request failed: ${error instanceof Error ? error.message : String(error)}`)
    }

    const text = await response.text()

    if (response.ok) {
      let parsed
      try {
        parsed = JSON.parse(text)
      } catch {
        throw new JevError(`Jev returned non-JSON (HTTP ${response.status}): ${text.slice(0, 300)}`)
      }
      const answers = parsed.answers
      if (!answers || typeof answers !== "object") {
        throw new JevError(`Jev response has no answers: ${text.slice(0, 300)}`)
      }
      return { answers, usage: parsed.usage }
    }

    // Free tier before first top-up allows one successful request per minute.
    if (response.status === 429 && attempt < 2) {
      await sleep(20000 * (attempt + 1), signal)
      continue
    }

    if (response.status === 401) {
      throw new JevError("Jev auth failed (401). Check that KENARI_API_KEY holds a valid kn-... key.")
    }
    if (response.status === 402) {
      throw new JevError(`Jev is not free for this request (402): ${text.slice(0, 300)}`)
    }
    throw new JevError(`Jev returned HTTP ${response.status}: ${text.slice(0, 300)}`)
  }

  throw new JevError("Jev request failed after retries (rate limited). Wait a minute and try again.")
}

function formatAnswers(answers) {
  const lines = []
  for (const [name, raw] of Object.entries(answers)) {
    if (!raw || typeof raw !== "object") {
      lines.push(`- ${name}: ${JSON.stringify(raw)}`)
      continue
    }
    const answer = raw
    const type = answer.type
    if (type === "noul") {
      const p = typeof answer.noul === "number" ? answer.noul : null
      lines.push(`- ${name} [noul]: P(yes) = ${p ?? "?"}${p === null ? "" : ` (${(p * 100).toFixed(1)}%)`}`)
    } else if (type === "choice") {
      const pick = answer.choice
      const conf = answer.confidence
      lines.push(`- ${name} [choice]: ${JSON.stringify(pick)}  confidence=${conf ?? "?"}`)
      if (answer.probabilities && typeof answer.probabilities === "object") {
        const sorted = Object.entries(answer.probabilities).sort((a, b) => b[1] - a[1])
        lines.push(`    distribution: ${sorted.map(([k, v]) => `${k}=${v}`).join(", ")}`)
      }
    } else if (type === "score") {
      lines.push(`- ${name} [score]: ${answer.score ?? "?"}  confidence=${answer.confidence ?? "?"}`)
      if (answer.legend && typeof answer.legend === "object") {
        const legend = Object.entries(answer.legend)
          .map(([k, v]) => `${k}=${v}`)
          .join(", ")
        lines.push(`    legend: ${legend}`)
      }
      if (answer.probabilities && typeof answer.probabilities === "object") {
        const probs = Object.entries(answer.probabilities)
          .map(([k, v]) => `${k}=${v}`)
          .join(", ")
        lines.push(`    distribution: ${probs}`)
      }
    } else {
      lines.push(`- ${name}: ${JSON.stringify(answer)}`)
    }
  }
  return lines.join("\n")
}

export default {
  id: "jev-decision",
  async setup(ctx) {
    const optionKey =
      typeof ctx.options?.apiKey === "string" && ctx.options.apiKey !== "" ? ctx.options.apiKey : undefined
    const apiKey = optionKey ?? process.env.KENARI_API_KEY
    if (!apiKey) {
      console.warn(
        "[jev-decision] No kenari key found. Set KENARI_API_KEY, or pass " +
          '{"options":{"apiKey":"kn-..."}} in the plugins entry. ' +
          "The jev_decide tool will fail until a key is available.",
      )
    }

    await ctx.tool.transform((editor) => {
      editor.namespace({
        name: "jev",
        description: "Typed decisions from TypeSafe Jev 1.13 (System One) via kenari.",
      })

      editor.add({
        name: "decide",
        description:
          "Ask TypeSafe Jev 1.13 (System One) for typed decisions about a state. " +
          "Provide `state` (facts) and `questions` (named questions). Each question has " +
          "type `noul` (yes/no with P(yes)), `choice` (one of up to 255 named options), " +
          "or `score` (an ordered 2-10 level scale). Returns typed answers with " +
          "probabilities/confidence. Use this for routing, classification, risk gates, " +
          "triage, and policy pre-screening. It does NOT generate prose and cannot be " +
          "used for conversation. Write state and instructions in Indonesian when the " +
          "surrounding task is in Indonesian.",
        input: {
          type: "object",
          properties: {
            state: {
              type: "string",
              description:
                "The facts Jev should judge. Minimum facts needed for the decision. " +
                "May be plain text or a JSON string. Max 24000 characters.",
            },
            questions: {
              type: "object",
              description:
                "Map of question id -> { type, instructions, criteria }. " +
                "noul: criteria optional object {true,false}. " +
                "choice: criteria required object mapping option -> description (2-255 options). " +
                "score: criteria required ordered low-to-high string array (2-10 levels).",
              additionalProperties: {
                type: "object",
                properties: {
                  type: { type: "string", enum: ["noul", "choice", "score"] },
                  instructions: { type: "string" },
                  criteria: {},
                },
                required: ["type", "instructions"],
              },
            },
          },
          required: ["state", "questions"],
          additionalProperties: false,
        },
        options: { namespace: "jev", codemode: true },
        execute: async (input, context) => {
          const { state, questions } = input

          if (typeof state !== "string" || state.trim() === "") {
            throw new JevError("`state` must be a non-empty string.")
          }
          if (state.length > MAX_STATE_CHARS) {
            throw new JevError(`\`state\` is ${state.length} characters; the cap is ${MAX_STATE_CHARS}.`)
          }
          if (!questions || typeof questions !== "object" || Array.isArray(questions)) {
            throw new JevError("`questions` must be a non-empty object.")
          }
          const names = Object.keys(questions)
          if (names.length === 0) {
            throw new JevError("`questions` must contain at least one question.")
          }
          if (names.length > MAX_QUESTIONS) {
            throw new JevError(`\`questions\` has ${names.length} entries; the cap is ${MAX_QUESTIONS}.`)
          }
          for (const name of names) {
            assertQuestion(name, questions[name])
          }
          if (!apiKey) {
            throw new JevError("KENARI_API_KEY is not set. Add a kn-... key to the environment.")
          }

          await context.progress({ status: `jev: ${names.length} question(s)` })
          const { answers, usage } = await callSystemOne(apiKey, state, questions, context.signal)

          return {
            content: `Jev (${MODEL}) answers:\n${formatAnswers(answers)}`,
            metadata: { model: MODEL, answers, usage },
          }
        },
      })
    })
  },
}
