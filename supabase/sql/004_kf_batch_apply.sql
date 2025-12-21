-- KiranaFlow: Batch sync apply function for faster bulk operations
-- Run after 003_kf_transaction_items_and_stricter_apply.sql

-- Batch apply function - processes multiple envelopes in a single transaction
-- Much faster than calling kf_apply_sync_envelope repeatedly
-- Call with: select public.kf_apply_sync_batch(<jsonb array>);
create or replace function public.kf_apply_sync_batch(envelopes jsonb)
returns jsonb
language plpgsql
security definer
as $$
declare
  v_envelope jsonb;
  v_result jsonb;
  v_results jsonb[] := '{}';
  v_idx int := 0;
begin
  -- Validate input is array
  if jsonb_typeof(envelopes) != 'array' then
    return jsonb_build_object('ok', false, 'error', 'Input must be an array of envelopes');
  end if;
  
  -- Process each envelope
  for v_envelope in select value from jsonb_array_elements(envelopes)
  loop
    v_idx := v_idx + 1;
    
    -- Call single apply function for each
    -- This keeps the logic DRY while benefiting from single transaction
    v_result := public.kf_apply_sync_envelope(v_envelope);
    v_results := array_append(v_results, v_result);
  end loop;
  
  return jsonb_build_object(
    'ok', true,
    'processed', v_idx,
    'results', to_jsonb(v_results)
  );
end;
$$;

-- Optimized index for faster sync op lookups
create index if not exists kf_sync_ops_device_sent_idx 
  on public.kf_sync_ops (device_id, sent_at_millis desc);

-- Optimized index for faster item lookups during transaction validation
create index if not exists kf_items_device_local_idx 
  on public.kf_items (device_id, local_id);

-- Optimized index for faster party lookups during transaction validation  
create index if not exists kf_parties_device_local_idx
  on public.kf_parties (device_id, local_id);

