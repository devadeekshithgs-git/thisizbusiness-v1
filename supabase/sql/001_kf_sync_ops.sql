-- KiranaFlow: sync ops log (idempotent outbox ingestion)
-- Run in Supabase SQL editor.

create table if not exists public.kf_sync_ops (
  id bigserial primary key,
  op_id text not null unique,
  device_id text not null,
  entity_type text not null,
  entity_id text null,
  op text not null,
  api_version int not null default 1,
  sent_at_millis bigint not null,
  body jsonb null,
  raw jsonb not null,
  received_at timestamptz not null default now()
);

create index if not exists kf_sync_ops_received_at_idx on public.kf_sync_ops (received_at desc);
create index if not exists kf_sync_ops_device_id_idx on public.kf_sync_ops (device_id);
create index if not exists kf_sync_ops_entity_idx on public.kf_sync_ops (entity_type, entity_id);



