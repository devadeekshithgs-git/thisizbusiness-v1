// KiranaFlow sync-apply Edge Function
// Handles single sync ops via POST to /sync-apply
// Expects JSON body: SyncEnvelope { apiVersion, deviceId, opId, sentAtMillis, entityType, entityId, op, body }

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, idempotency-key, x-device-id, x-preview-method, x-preview-path",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface SyncEnvelope {
  apiVersion?: number;
  deviceId: string;
  opId: string;
  sentAtMillis?: number;
  entityType: string;
  entityId?: string;
  op: string;
  body?: Record<string, unknown>;
}

interface SyncResult {
  ok: boolean;
  message?: string;
  replay?: boolean;
  opId?: string;
}

Deno.serve(async (req) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ ok: false, message: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    const envelope: SyncEnvelope = await req.json();

    // Validate required fields
    if (!envelope.opId || !envelope.deviceId || !envelope.entityType || !envelope.op) {
      return new Response(
        JSON.stringify({ ok: false, message: "Missing required fields (opId, deviceId, entityType, op)" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Call the idempotent apply function
    const { data, error } = await supabase.rpc("kf_apply_sync_envelope", {
      envelope: envelope,
    });

    if (error) {
      console.error("RPC error:", error);
      return new Response(
        JSON.stringify({ ok: false, message: error.message }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const result: SyncResult = data as SyncResult;
    return new Response(JSON.stringify(result), {
      status: result.ok ? 200 : 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Unhandled error:", err);
    return new Response(
      JSON.stringify({ ok: false, message: err instanceof Error ? err.message : "Unknown error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
