delete from public.operation_audit_logs
where created_at < now() - interval '1 year';
