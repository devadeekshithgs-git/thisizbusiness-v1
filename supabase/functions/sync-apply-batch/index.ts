// KiranaFlow sync-apply-batch Edge Function
// Handles batch sync ops via POST to /sync-apply-batch
// Expects JSON body: { ops: Array<{ envelope: SyncEnvelope, preview?: { method, path } }> }
// Returns: { results: Array<{ ok: boolean, message?: string, opId?: string }> }

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-batch-count",
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

interface BatchOp {
  envelope: SyncEnvelope;
  preview?: { method: string; path: string };
}

interface BatchRequest {
  ops: BatchOp[];
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

    const batchRequest: BatchRequest = await req.json();

    if (!batchRequest.ops || !Array.isArray(batchRequest.ops)) {
      return new Response(
        JSON.stringify({ ok: false, message: "Missing or invalid 'ops' array" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Process all ops in parallel for speed
    const results = await Promise.all(
      batchRequest.ops.map(async (op): Promise<SyncResult> => {
        const envelope = op.envelope;

        // Validate required fields
        if (!envelope.opId || !envelope.deviceId || !envelope.entityType || !envelope.op) {
          return {
            ok: false,
            message: "Missing required fields (opId, deviceId, entityType, op)",
            opId: envelope.opId,
          };
        }

        try {
          // Call the idempotent apply function
          const { data, error } = await supabase.rpc("kf_apply_sync_envelope", {
            envelope: envelope,
          });

          if (error) {
            console.error(`RPC error for opId ${envelope.opId}:`, error);
            return {
              ok: false,
              message: error.message,
              opId: envelope.opId,
            };
          }

          return data as SyncResult;
        } catch (err) {
          console.error(`Error processing opId ${envelope.opId}:`, err);
          return {
            ok: false,
            message: err instanceof Error ? err.message : "Unknown error",
            opId: envelope.opId,
          };
        }
      })
    );

    // Return batch results
    return new Response(
      JSON.stringify({ results }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (err) {
    console.error("Unhandled error:", err);
    return new Response(
      JSON.stringify({ ok: false, message: err instanceof Error ? err.message : "Unknown error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});








