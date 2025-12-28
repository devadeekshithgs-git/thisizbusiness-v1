-- KiranaFlow: Transaction controlled edits (status + adjustments + audit logs)
-- Run after:
--   001_kf_sync_ops.sql
--   002_kf_core_tables_and_apply.sql
--   003_kf_transaction_items_and_stricter_apply.sql
--   004_kf_batch_apply.sql

-- 1) Extend transactions for controlled edit status + GST filing lock
alter table public.kf_transactions
  add column if not exists status text default 'POSTED',
  add column if not exists gst_filed_period text;

-- 2) Adjustment + audit tables (device-scoped local IDs)
create table if not exists public.kf_users (
  device_id text not null,
  local_id text not null,
  name text not null,
  role text not null, -- OWNER | MANAGER | CASHIER
  pin text null,
  is_active boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);

create table if not exists public.kf_transaction_adjustments (
  device_id text not null,
  local_id text not null,
  original_transaction_local_id text not null,
  adjustment_type text not null, -- EDIT | CANCEL | CORRECTION
  reason text not null,
  user_local_id text null,
  net_amount_change numeric null,
  gst_type text null, -- CREDIT_NOTE | DEBIT_NOTE
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);
create index if not exists kf_transaction_adjustments_orig_idx
  on public.kf_transaction_adjustments (device_id, original_transaction_local_id);

create table if not exists public.kf_transaction_adjustment_items (
  device_id text not null,
  adjustment_local_id text not null,
  line_no int not null,
  item_local_id text null,
  name text null,
  quantity_delta numeric not null default 0,
  price_delta numeric not null default 0,
  tax_delta numeric not null default 0,
  primary key (device_id, adjustment_local_id, line_no)
);
create index if not exists kf_transaction_adjustment_items_item_idx
  on public.kf_transaction_adjustment_items (device_id, item_local_id);

create table if not exists public.kf_stock_movements (
  device_id text not null,
  local_id text not null,
  item_local_id text not null,
  delta numeric not null,
  source text not null, -- SALE | PURCHASE | EDIT | ADJUSTMENT | VOID
  transaction_local_id text null,
  adjustment_local_id text null,
  user_local_id text null,
  reason text null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);
create index if not exists kf_stock_movements_item_idx
  on public.kf_stock_movements (device_id, item_local_id, created_at desc);

-- 3) Replace apply function to persist transaction.status/gst_filed_period when provided.
--    (Other entities remain unchanged; this is minimal and backward-compatible.)
create or replace function public.kf_apply_sync_envelope(envelope jsonb)
returns jsonb
language plpgsql
security definer
as $$
declare
  v_op_id text;
  v_device_id text;
  v_entity_type text;
  v_entity_id text;
  v_op text;
  v_body jsonb;
  v_inserted boolean := false;
  v_row_count integer := 0;
  v_tx_local_id text;
  v_customer_local_id text;
  v_vendor_local_id text;
  v_items jsonb;
  v_missing text;
  v_client_updated_at timestamptz;
begin
  v_op_id := envelope->>'opId';
  v_device_id := envelope->>'deviceId';
  v_entity_type := envelope->>'entityType';
  v_entity_id := envelope->>'entityId';
  v_op := envelope->>'op';
  v_body := envelope->'body';

  if v_op_id is null or length(v_op_id) = 0 then
    return jsonb_build_object('ok', false, 'error', 'Missing opId');
  end if;
  if v_device_id is null or length(v_device_id) = 0 then
    return jsonb_build_object('ok', false, 'error', 'Missing deviceId');
  end if;
  if v_entity_type is null or length(v_entity_type) = 0 then
    return jsonb_build_object('ok', false, 'error', 'Missing entityType');
  end if;
  if v_op is null or length(v_op) = 0 then
    return jsonb_build_object('ok', false, 'error', 'Missing op');
  end if;

  -- Idempotent log insert. If already present, treat as replay and return ok.
  insert into public.kf_sync_ops(op_id, device_id, entity_type, entity_id, op, api_version, sent_at_millis, body, raw)
  values (
    v_op_id,
    v_device_id,
    v_entity_type,
    v_entity_id,
    v_op,
    coalesce((envelope->>'apiVersion')::int, 1),
    coalesce((envelope->>'sentAtMillis')::bigint, (extract(epoch from now())*1000)::bigint),
    v_body,
    envelope
  )
  on conflict (op_id) do nothing;
  get diagnostics v_row_count = row_count;
  v_inserted := v_row_count > 0;

  if not v_inserted then
    return jsonb_build_object('ok', true, 'replay', true, 'opId', v_op_id);
  end if;

  -- Prefer client-side updatedAt if provided (millis), else now().
  begin
    if v_body ? 'updatedAt' then
      v_client_updated_at := to_timestamp(((v_body->>'updatedAt')::bigint) / 1000.0);
    else
      v_client_updated_at := now();
    end if;
  exception when others then
    v_client_updated_at := now();
  end;

  -- Apply mutation based on entityType/op.
  if v_entity_type = 'ITEM' then
    if v_op = 'UPSERT' then
      insert into public.kf_items(
        device_id, local_id, name, category, price, cost_price, stock,
        gst_percentage, reorder_point, vendor_local_id, rack_location, barcode,
        image_uri, expiry_date_millis, updated_at
      )
      values (
        v_device_id,
        coalesce(v_entity_id, v_body->>'id'),
        coalesce(v_body->>'name',''),
        v_body->>'category',
        nullif(v_body->>'price','')::numeric,
        nullif(v_body->>'costPrice','')::numeric,
        nullif(v_body->>'stock','')::int,
        nullif(v_body->>'gstPercentage','')::numeric,
        nullif(v_body->>'reorderPoint','')::int,
        (v_body->>'vendorId'),
        v_body->>'rackLocation',
        v_body->>'barcode',
        v_body->>'imageUri',
        nullif(v_body->>'expiryDateMillis','')::bigint,
        now()
      )
      on conflict (device_id, local_id) do update set
        name = excluded.name,
        category = excluded.category,
        price = excluded.price,
        cost_price = excluded.cost_price,
        stock = excluded.stock,
        gst_percentage = excluded.gst_percentage,
        reorder_point = excluded.reorder_point,
        vendor_local_id = excluded.vendor_local_id,
        rack_location = excluded.rack_location,
        barcode = excluded.barcode,
        image_uri = excluded.image_uri,
        expiry_date_millis = excluded.expiry_date_millis,
        updated_at = now();
    elsif v_op = 'DELETE' then
      delete from public.kf_items where device_id = v_device_id and local_id = coalesce(v_entity_id, v_body->>'id');
    end if;

  elsif v_entity_type = 'PARTY' then
    if v_op = 'UPSERT' or v_op = 'UPSERT_CUSTOMER' or v_op = 'UPSERT_VENDOR' then
      insert into public.kf_parties(
        device_id, local_id, type, name, phone, gst_number, balance, updated_at
      )
      values (
        v_device_id,
        coalesce(v_entity_id, v_body->>'id'),
        coalesce(v_body->>'type',''),
        coalesce(v_body->>'name',''),
        v_body->>'phone',
        v_body->>'gstNumber',
        nullif(v_body->>'balance','')::numeric,
        now()
      )
      on conflict (device_id, local_id) do update set
        type = excluded.type,
        name = excluded.name,
        phone = excluded.phone,
        gst_number = excluded.gst_number,
        balance = excluded.balance,
        updated_at = now();
    elsif v_op = 'DELETE' then
      delete from public.kf_parties where device_id = v_device_id and local_id = coalesce(v_entity_id, v_body->>'id');
    end if;

  elsif v_entity_type = 'TRANSACTION' then
    if v_op = 'DELETE' then
      v_tx_local_id := coalesce(v_entity_id, v_body->>'id');
      delete from public.kf_transaction_items where device_id = v_device_id and transaction_local_id = v_tx_local_id;
      delete from public.kf_transactions where device_id = v_device_id and local_id = v_tx_local_id;
    else
      v_tx_local_id := coalesce(v_entity_id, v_body->>'localId', v_body->>'id');
      v_customer_local_id := nullif(v_body->>'customerId','');
      v_vendor_local_id := nullif(v_body->>'vendorId','');
      v_items := v_body->'items';

      -- Dependency checks (return ok=false, but keep kf_sync_ops log row).
      if v_customer_local_id is not null then
        select local_id into v_missing
        from public.kf_parties
        where device_id = v_device_id and local_id = v_customer_local_id
        limit 1;
        if v_missing is null then
          return jsonb_build_object('ok', false, 'error', 'Missing dependency: PARTY(customerId='||v_customer_local_id||')', 'opId', v_op_id);
        end if;
      end if;
      if v_vendor_local_id is not null then
        select local_id into v_missing
        from public.kf_parties
        where device_id = v_device_id and local_id = v_vendor_local_id
        limit 1;
        if v_missing is null then
          return jsonb_build_object('ok', false, 'error', 'Missing dependency: PARTY(vendorId='||v_vendor_local_id||')', 'opId', v_op_id);
        end if;
      end if;

      -- If items include itemId, enforce that those items exist remotely.
      if v_items is not null then
        if exists (
          select 1
          from jsonb_array_elements(v_items) as e
          where (e->>'itemId') is not null
            and not exists (
              select 1 from public.kf_items i
              where i.device_id = v_device_id and i.local_id = (e->>'itemId')
            )
        ) then
          return jsonb_build_object('ok', false, 'error', 'Missing dependency: ITEM referenced in transaction.items', 'opId', v_op_id);
        end if;
      end if;

      insert into public.kf_transactions(
        device_id, local_id, type, payment_mode, customer_local_id, vendor_local_id,
        amount, items, note, status, gst_filed_period, updated_at
      )
      values (
        v_device_id,
        v_tx_local_id,
        coalesce(v_body->>'type', ''),
        v_body->>'paymentMode',
        v_customer_local_id,
        v_vendor_local_id,
        nullif(v_body->>'amount','')::numeric,
        v_items,
        v_body->>'note',
        coalesce(nullif(v_body->>'status',''), 'POSTED'),
        nullif(v_body->>'gstFiledPeriod',''),
        v_client_updated_at
      )
      on conflict (device_id, local_id) do update set
        type = excluded.type,
        payment_mode = excluded.payment_mode,
        customer_local_id = excluded.customer_local_id,
        vendor_local_id = excluded.vendor_local_id,
        amount = excluded.amount,
        items = coalesce(excluded.items, public.kf_transactions.items),
        note = excluded.note,
        status = excluded.status,
        gst_filed_period = excluded.gst_filed_period,
        updated_at = excluded.updated_at;

      -- Normalize line items if present.
      if v_items is not null then
        delete from public.kf_transaction_items where device_id = v_device_id and transaction_local_id = v_tx_local_id;
        insert into public.kf_transaction_items(device_id, transaction_local_id, line_no, item_local_id, name, qty, price)
        select
          v_device_id,
          v_tx_local_id,
          (x.ordinality)::int as line_no,
          nullif(x.value->>'itemId','') as item_local_id,
          nullif(coalesce(x.value->>'name', x.value->>'itemNameSnapshot'),'') as name,
          coalesce(nullif(x.value->>'qty','')::int, 1) as qty,
          coalesce(nullif(x.value->>'price','')::numeric, 0) as price
        from jsonb_array_elements(v_items) with ordinality as x(value, ordinality);
      end if;
    end if;

  elsif v_entity_type = 'TRANSACTION_ITEM' then
    if v_op = 'UPSERT_MANY' then
      v_tx_local_id := coalesce(v_entity_id, v_body->>'transactionLocalId');
      v_items := v_body->'items';
      if v_tx_local_id is null then
        return jsonb_build_object('ok', false, 'error', 'Missing transactionLocalId for UPSERT_MANY', 'opId', v_op_id);
      end if;

      if not exists (select 1 from public.kf_transactions t where t.device_id = v_device_id and t.local_id = v_tx_local_id) then
        return jsonb_build_object('ok', false, 'error', 'Missing dependency: TRANSACTION(localId='||v_tx_local_id||')', 'opId', v_op_id);
      end if;

      if v_items is not null then
        if exists (
          select 1
          from jsonb_array_elements(v_items) as e
          where (e->>'itemId') is not null
            and not exists (
              select 1 from public.kf_items i
              where i.device_id = v_device_id and i.local_id = (e->>'itemId')
            )
        ) then
          return jsonb_build_object('ok', false, 'error', 'Missing dependency: ITEM referenced in transaction_items', 'opId', v_op_id);
        end if;

        delete from public.kf_transaction_items where device_id = v_device_id and transaction_local_id = v_tx_local_id;
        insert into public.kf_transaction_items(device_id, transaction_local_id, line_no, item_local_id, name, qty, price)
        select
          v_device_id,
          v_tx_local_id,
          (x.ordinality)::int as line_no,
          nullif(x.value->>'itemId','') as item_local_id,
          nullif(coalesce(x.value->>'name', x.value->>'itemNameSnapshot'),'') as name,
          coalesce(nullif(x.value->>'qty','')::int, 1) as qty,
          coalesce(nullif(x.value->>'price','')::numeric, 0) as price
        from jsonb_array_elements(v_items) with ordinality as x(value, ordinality);

        update public.kf_transactions
        set items = v_items,
            updated_at = v_client_updated_at
        where device_id = v_device_id and local_id = v_tx_local_id;
      end if;
    end if;

  elsif v_entity_type = 'REMINDER' then
    if v_op = 'UPSERT' then
      insert into public.kf_reminders(
        device_id, local_id, type, ref_local_id, title, due_at_millis, note, is_done, updated_at
      )
      values (
        v_device_id,
        coalesce(v_entity_id, v_body->>'localId', v_body->>'id'),
        coalesce(v_body->>'type',''),
        (v_body->>'refId'),
        coalesce(v_body->>'title',''),
        nullif(v_body->>'dueAt','')::bigint,
        v_body->>'note',
        false,
        now()
      )
      on conflict (device_id, local_id) do update set
        type = excluded.type,
        ref_local_id = excluded.ref_local_id,
        title = excluded.title,
        due_at_millis = excluded.due_at_millis,
        note = excluded.note,
        updated_at = now();
    elsif v_op = 'MARK_DONE' then
      update public.kf_reminders
      set is_done = true,
          updated_at = now()
      where device_id = v_device_id and local_id = coalesce(v_entity_id, v_body->>'id');
    elsif v_op = 'DELETE' then
      delete from public.kf_reminders where device_id = v_device_id and local_id = coalesce(v_entity_id, v_body->>'id');
    end if;
  end if;

  return jsonb_build_object('ok', true, 'replay', false, 'opId', v_op_id);
end;
$$;



