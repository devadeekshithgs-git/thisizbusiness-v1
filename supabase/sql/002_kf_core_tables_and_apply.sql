-- KiranaFlow: core remote tables + idempotent apply RPC
-- Run after 001_kf_sync_ops.sql

-- Core tables (device-scoped local IDs)
create table if not exists public.kf_items (
  device_id text not null,
  local_id text not null,
  name text not null,
  category text null,
  price numeric null,
  cost_price numeric null,
  stock int null,
  gst_percentage numeric null,
  reorder_point int null,
  vendor_local_id text null,
  rack_location text null,
  barcode text null,
  image_uri text null,
  expiry_date_millis bigint null,
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);

create table if not exists public.kf_parties (
  device_id text not null,
  local_id text not null,
  type text not null, -- CUSTOMER | VENDOR
  name text not null,
  phone text null,
  gst_number text null,
  balance numeric null,
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);

create table if not exists public.kf_transactions (
  device_id text not null,
  local_id text not null,
  type text not null, -- SALE | EXPENSE | INCOME
  payment_mode text null,
  customer_local_id text null,
  vendor_local_id text null,
  amount numeric null,
  items jsonb null, -- canonical line items array for SALE/UPSERT_MANY
  note text null,
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);

create table if not exists public.kf_reminders (
  device_id text not null,
  local_id text not null,
  type text not null, -- ITEM | VENDOR | GENERAL
  ref_local_id text null,
  title text not null,
  due_at_millis bigint not null,
  note text null,
  is_done boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (device_id, local_id)
);

create index if not exists kf_items_device_idx on public.kf_items (device_id);
create index if not exists kf_parties_device_idx on public.kf_parties (device_id);
create index if not exists kf_transactions_device_idx on public.kf_transactions (device_id);
create index if not exists kf_reminders_device_idx on public.kf_reminders (device_id);

-- Idempotent apply function.
-- Call with: select public.kf_apply_sync_envelope(<jsonb>);
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
  begin
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
  exception when others then
    return jsonb_build_object('ok', false, 'error', 'Failed to write kf_sync_ops');
  end;

  if not v_inserted then
    return jsonb_build_object('ok', true, 'replay', true, 'opId', v_op_id);
  end if;

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
        (v_body->>'price')::numeric,
        (v_body->>'costPrice')::numeric,
        (v_body->>'stock')::int,
        (v_body->>'gstPercentage')::numeric,
        (v_body->>'reorderPoint')::int,
        (v_body->>'vendorId'),
        v_body->>'rackLocation',
        v_body->>'barcode',
        v_body->>'imageUri',
        (v_body->>'expiryDateMillis')::bigint,
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
        (v_body->>'balance')::numeric,
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
      delete from public.kf_transactions where device_id = v_device_id and local_id = coalesce(v_entity_id, v_body->>'id');
    else
      -- CREATE_* or UPSERT: upsert into transactions (device_id, local_id)
      insert into public.kf_transactions(
        device_id, local_id, type, payment_mode, customer_local_id, vendor_local_id, amount, items, note, updated_at
      )
      values (
        v_device_id,
        coalesce(v_entity_id, v_body->>'localId', v_body->>'id'),
        coalesce(v_body->>'type', ''),
        v_body->>'paymentMode',
        (v_body->>'customerId'),
        (v_body->>'vendorId'),
        (v_body->>'amount')::numeric,
        v_body->'items',
        v_body->>'note',
        now()
      )
      on conflict (device_id, local_id) do update set
        type = excluded.type,
        payment_mode = excluded.payment_mode,
        customer_local_id = excluded.customer_local_id,
        vendor_local_id = excluded.vendor_local_id,
        amount = excluded.amount,
        items = coalesce(excluded.items, public.kf_transactions.items),
        note = excluded.note,
        updated_at = now();
    end if;
  elsif v_entity_type = 'TRANSACTION_ITEM' then
    if v_op = 'UPSERT_MANY' then
      update public.kf_transactions
      set items = v_body->'items',
          updated_at = now()
      where device_id = v_device_id and local_id = coalesce(v_entity_id, v_body->>'transactionLocalId');
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
        (v_body->>'dueAt')::bigint,
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



