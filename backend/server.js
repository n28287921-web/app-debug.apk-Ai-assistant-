require("dotenv").config();
const express = require("express");
const cors = require("cors");
const admin = require("firebase-admin");

const app = express();
app.use(cors());
app.use(express.json({ limit: "1mb" }));

if (!admin.apps.length && process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
  });
}

async function authenticateRequest(req, res, next) {
  const authHeader = req.headers.authorization || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  if (!token) {
    return res.status(401).json({ error: "Missing Authorization Bearer token" });
  }

  if (process.env.JARVIS_SHARED_TOKEN && token === process.env.JARVIS_SHARED_TOKEN) {
    return next();
  }

  if (admin.apps.length) {
    try {
      req.user = await admin.auth().verifyIdToken(token);
      return next();
    } catch (err) {
      return res.status(401).json({ error: "Invalid Firebase ID token" });
    }
  }

  return res.status(401).json({ error: "Server auth not configured" });
}

app.get("/health", (_req, res) => {
  res.json({ status: "ok", service: "jarvis-vip-openai-proxy" });
});

app.post("/api/jarvis/chat", authenticateRequest, async (req, res) => {
  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) {
    return res.status(500).json({ error: "OPENAI_API_KEY is not configured on the backend server." });
  }

  try {
    const { messages = [], model } = req.body || {};
    const selectedModel = model || process.env.OPENAI_DEFAULT_MODEL || "gpt-4o-mini";

    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: selectedModel,
        temperature: 0.5,
        response_format: { type: "json_object" },
        messages,
      }),
    });

    const data = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({
        error: data?.error?.message || "OpenAI upstream error",
      });
    }

    const content = data?.choices?.[0]?.message?.content || "{}";
    try {
      const parsed = JSON.parse(content);
      return res.json({
        reply: parsed.reply || "Done.",
        language: parsed.language || "bilingual",
        actions: Array.isArray(parsed.actions) ? parsed.actions : [],
      });
    } catch {
      return res.json({
        reply: content,
        language: "bilingual",
        actions: [],
      });
    }
  } catch (err) {
    return res.status(500).json({ error: err.message || "Proxy failure" });
  }
});

const port = process.env.PORT || 8080;
app.listen(port, () => {
  console.log(`Jarvis VIP OpenAI proxy listening on port ${port}`);
});
