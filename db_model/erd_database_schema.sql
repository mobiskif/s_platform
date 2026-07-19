-- ============================================================
-- Схема базы данных платформы монтажа и сервисного обслуживания
-- оборудования — 6 модулей, 30 таблиц
-- Источник: Бизнес-план v3, раздел 3 «Продукт» и Приложение А
-- СУБД: PostgreSQL 14+
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- ENUM-типы
-- ------------------------------------------------------------
CREATE TYPE equipment_sale_channel_enum AS ENUM ('direct', 'dealer');
CREATE TYPE equipment_warranty_status_enum AS ENUM ('active', 'expired');
CREATE TYPE equipment_status_enum AS ENUM ('active', 'decommissioned');
CREATE TYPE documents_doc_type_enum AS ENUM ('manual', 'specification', 'certificate');
CREATE TYPE contractors_type_enum AS ENUM ('staff', 'contractor');
CREATE TYPE installation_jobs_status_enum AS ENUM ('planned', 'in_progress', 'completed');
CREATE TYPE acceptance_acts_signature_status_enum AS ENUM ('pending', 'signed');
CREATE TYPE sla_policies_priority_enum AS ENUM ('critical', 'high', 'standard');
CREATE TYPE service_tickets_channel_enum AS ENUM ('phone', 'email', 'portal', 'app');
CREATE TYPE service_tickets_status_enum AS ENUM ('new', 'assigned', 'in_progress', 'closed');
CREATE TYPE ticket_actions_action_type_enum AS ENUM ('note', 'photo', 'status_change', 'part_used');
CREATE TYPE warehouses_type_enum AS ENUM ('central', 'regional', 'engineer_van');
CREATE TYPE claims_severity_enum AS ENUM ('low', 'medium', 'high', 'critical');
CREATE TYPE claims_status_enum AS ENUM ('new', 'reviewed', 'actioned');
CREATE TYPE product_improvements_status_enum AS ENUM ('proposed', 'in_progress', 'implemented');

-- ------------------------------------------------------------
-- Модуль 1. Электронный паспорт оборудования
-- ------------------------------------------------------------
CREATE TABLE manufacturers (
    manufacturer_id TEXT NOT NULL,
    name TEXT,
    country TEXT,
    industry_segment TEXT,
    created_at DATE
    , CONSTRAINT pk_manufacturers PRIMARY KEY (manufacturer_id)
);
COMMENT ON TABLE manufacturers IS 'Производители (тенанты платформы)';

CREATE TABLE clients (
    client_id TEXT NOT NULL,
    manufacturer_id TEXT,
    name TEXT,
    inn TEXT,
    contact_person TEXT,
    phone TEXT,
    email TEXT
    , CONSTRAINT pk_clients PRIMARY KEY (client_id)
    , CONSTRAINT fk_clients_manufacturer_id FOREIGN KEY (manufacturer_id) REFERENCES manufacturers (manufacturer_id) ON DELETE RESTRICT
);
COMMENT ON TABLE clients IS 'Клиенты-эксплуатанты';

CREATE TABLE sites (
    site_id TEXT NOT NULL,
    client_id TEXT,
    address TEXT,
    city TEXT,
    region TEXT
    , CONSTRAINT pk_sites PRIMARY KEY (site_id)
    , CONSTRAINT fk_sites_client_id FOREIGN KEY (client_id) REFERENCES clients (client_id) ON DELETE RESTRICT
);
COMMENT ON TABLE sites IS 'Объекты эксплуатации';

CREATE TABLE equipment_models (
    model_id TEXT NOT NULL,
    manufacturer_id TEXT,
    model_name TEXT,
    category TEXT,
    default_warranty_months INTEGER
    , CONSTRAINT pk_equipment_models PRIMARY KEY (model_id)
    , CONSTRAINT fk_equipment_models_manufacturer_id FOREIGN KEY (manufacturer_id) REFERENCES manufacturers (manufacturer_id) ON DELETE RESTRICT
);
COMMENT ON TABLE equipment_models IS 'Модели оборудования (справочник)';

CREATE TABLE dealers (
    dealer_id TEXT NOT NULL,
    manufacturer_id TEXT,
    name TEXT,
    region TEXT,
    contact_phone TEXT,
    contact_email TEXT
    , CONSTRAINT pk_dealers PRIMARY KEY (dealer_id)
    , CONSTRAINT fk_dealers_manufacturer_id FOREIGN KEY (manufacturer_id) REFERENCES manufacturers (manufacturer_id) ON DELETE RESTRICT
);
COMMENT ON TABLE dealers IS 'Дилеры (партнёры по продаже оборудования)';

CREATE TABLE equipment (
    equipment_id TEXT NOT NULL,
    model_id TEXT,
    client_id TEXT,
    site_id TEXT,
    serial_number TEXT,
    sale_channel equipment_sale_channel_enum,
    dealer_id TEXT,
    ship_date DATE,
    install_date DATE,
    warranty_end_date DATE,
    warranty_status equipment_warranty_status_enum,
    operating_hours INTEGER,
    status equipment_status_enum
    , CONSTRAINT pk_equipment PRIMARY KEY (equipment_id)
    , CONSTRAINT fk_equipment_model_id FOREIGN KEY (model_id) REFERENCES equipment_models (model_id) ON DELETE RESTRICT
    , CONSTRAINT fk_equipment_client_id FOREIGN KEY (client_id) REFERENCES clients (client_id) ON DELETE RESTRICT
    , CONSTRAINT fk_equipment_site_id FOREIGN KEY (site_id) REFERENCES sites (site_id) ON DELETE RESTRICT
    , CONSTRAINT fk_equipment_dealer_id FOREIGN KEY (dealer_id) REFERENCES dealers (dealer_id) ON DELETE RESTRICT
);
COMMENT ON TABLE equipment IS 'Оборудование (электронный паспорт)';

CREATE TABLE documents (
    document_id TEXT NOT NULL,
    equipment_id TEXT,
    doc_type documents_doc_type_enum,
    file_name TEXT,
    uploaded_at DATE
    , CONSTRAINT pk_documents PRIMARY KEY (document_id)
    , CONSTRAINT fk_documents_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
);
COMMENT ON TABLE documents IS 'Техдокументация';

CREATE TABLE sensor_readings (
    reading_id TEXT NOT NULL,
    equipment_id TEXT,
    metric_name TEXT,
    value NUMERIC(12,2),
    unit TEXT,
    recorded_at TIMESTAMP
    , CONSTRAINT pk_sensor_readings PRIMARY KEY (reading_id)
    , CONSTRAINT fk_sensor_readings_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
);
COMMENT ON TABLE sensor_readings IS 'Данные с датчиков (опционально, IoT)';

-- ------------------------------------------------------------
-- Модуль 2. Монтаж и пусконаладка
-- ------------------------------------------------------------
CREATE TABLE contractors (
    contractor_id TEXT NOT NULL,
    name TEXT,
    type contractors_type_enum,
    region TEXT,
    contact_phone TEXT
    , CONSTRAINT pk_contractors PRIMARY KEY (contractor_id)
);
COMMENT ON TABLE contractors IS 'Монтажные бригады / подрядчики';

CREATE TABLE installation_jobs (
    job_id TEXT NOT NULL,
    equipment_id TEXT,
    contractor_id TEXT,
    scheduled_date DATE,
    completed_date DATE,
    status installation_jobs_status_enum
    , CONSTRAINT pk_installation_jobs PRIMARY KEY (job_id)
    , CONSTRAINT fk_installation_jobs_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
    , CONSTRAINT fk_installation_jobs_contractor_id FOREIGN KEY (contractor_id) REFERENCES contractors (contractor_id) ON DELETE RESTRICT
);
COMMENT ON TABLE installation_jobs IS 'Монтажные работы';

CREATE TABLE checklist_templates (
    template_id TEXT NOT NULL,
    name TEXT,
    equipment_category TEXT
    , CONSTRAINT pk_checklist_templates PRIMARY KEY (template_id)
);
COMMENT ON TABLE checklist_templates IS 'Шаблоны чек-листов приёмки';

CREATE TABLE checklist_items (
    item_id TEXT NOT NULL,
    job_id TEXT,
    template_id TEXT,
    description TEXT,
    is_checked BOOLEAN,
    photo_url TEXT
    , CONSTRAINT pk_checklist_items PRIMARY KEY (item_id)
    , CONSTRAINT fk_checklist_items_job_id FOREIGN KEY (job_id) REFERENCES installation_jobs (job_id) ON DELETE RESTRICT
    , CONSTRAINT fk_checklist_items_template_id FOREIGN KEY (template_id) REFERENCES checklist_templates (template_id) ON DELETE RESTRICT
);
COMMENT ON TABLE checklist_items IS 'Пункты чек-листа (по конкретному монтажу)';

CREATE TABLE acceptance_acts (
    act_id TEXT NOT NULL,
    job_id TEXT,
    signed_by TEXT,
    signature_date DATE,
    signature_status acceptance_acts_signature_status_enum
    , CONSTRAINT pk_acceptance_acts PRIMARY KEY (act_id)
    , CONSTRAINT fk_acceptance_acts_job_id FOREIGN KEY (job_id) REFERENCES installation_jobs (job_id) ON DELETE RESTRICT
);
COMMENT ON TABLE acceptance_acts IS 'Акты приёмки монтажа';

-- ------------------------------------------------------------
-- Модуль 3. Заявки и выездное обслуживание (FSM)
-- ------------------------------------------------------------
CREATE TABLE engineers (
    engineer_id TEXT NOT NULL,
    name TEXT,
    skills TEXT,
    region TEXT,
    phone TEXT
    , CONSTRAINT pk_engineers PRIMARY KEY (engineer_id)
);
COMMENT ON TABLE engineers IS 'Сервисные инженеры';

CREATE TABLE sla_policies (
    policy_id TEXT NOT NULL,
    priority sla_policies_priority_enum,
    response_time_hours INTEGER,
    resolution_time_hours INTEGER
    , CONSTRAINT pk_sla_policies PRIMARY KEY (policy_id)
);
COMMENT ON TABLE sla_policies IS 'Политики SLA';

CREATE TABLE service_tickets (
    ticket_id TEXT NOT NULL,
    equipment_id TEXT,
    client_id TEXT,
    channel service_tickets_channel_enum,
    policy_id TEXT,
    status service_tickets_status_enum,
    created_at TIMESTAMP,
    closed_at TIMESTAMP
    , CONSTRAINT pk_service_tickets PRIMARY KEY (ticket_id)
    , CONSTRAINT fk_service_tickets_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
    , CONSTRAINT fk_service_tickets_client_id FOREIGN KEY (client_id) REFERENCES clients (client_id) ON DELETE RESTRICT
    , CONSTRAINT fk_service_tickets_policy_id FOREIGN KEY (policy_id) REFERENCES sla_policies (policy_id) ON DELETE RESTRICT
);
COMMENT ON TABLE service_tickets IS 'Заявки на сервис';

CREATE TABLE ticket_assignments (
    assignment_id TEXT NOT NULL,
    ticket_id TEXT,
    engineer_id TEXT,
    assigned_at TIMESTAMP
    , CONSTRAINT pk_ticket_assignments PRIMARY KEY (assignment_id)
    , CONSTRAINT fk_ticket_assignments_ticket_id FOREIGN KEY (ticket_id) REFERENCES service_tickets (ticket_id) ON DELETE RESTRICT
    , CONSTRAINT fk_ticket_assignments_engineer_id FOREIGN KEY (engineer_id) REFERENCES engineers (engineer_id) ON DELETE RESTRICT
);
COMMENT ON TABLE ticket_assignments IS 'Назначения инженеров на заявки';

CREATE TABLE ticket_actions (
    action_id TEXT NOT NULL,
    ticket_id TEXT,
    action_type ticket_actions_action_type_enum,
    description TEXT,
    action_at TIMESTAMP
    , CONSTRAINT pk_ticket_actions PRIMARY KEY (action_id)
    , CONSTRAINT fk_ticket_actions_ticket_id FOREIGN KEY (ticket_id) REFERENCES service_tickets (ticket_id) ON DELETE RESTRICT
);
COMMENT ON TABLE ticket_actions IS 'Действия / лог по заявке';

-- ------------------------------------------------------------
-- Модуль 4. Склад запчастей и логистика
-- ------------------------------------------------------------
CREATE TABLE parts (
    part_id TEXT NOT NULL,
    name TEXT,
    compatible_model_id TEXT,
    unit_price NUMERIC(12,2),
    unit TEXT
    , CONSTRAINT pk_parts PRIMARY KEY (part_id)
    , CONSTRAINT fk_parts_compatible_model_id FOREIGN KEY (compatible_model_id) REFERENCES equipment_models (model_id) ON DELETE RESTRICT
);
COMMENT ON TABLE parts IS 'Запчасти (справочник)';

CREATE TABLE warehouses (
    warehouse_id TEXT NOT NULL,
    name TEXT,
    region TEXT,
    type warehouses_type_enum
    , CONSTRAINT pk_warehouses PRIMARY KEY (warehouse_id)
);
COMMENT ON TABLE warehouses IS 'Склады';

CREATE TABLE stock_levels (
    stock_id TEXT NOT NULL,
    warehouse_id TEXT,
    part_id TEXT,
    quantity INTEGER,
    last_updated DATE
    , CONSTRAINT pk_stock_levels PRIMARY KEY (stock_id)
    , CONSTRAINT fk_stock_levels_warehouse_id FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id) ON DELETE RESTRICT
    , CONSTRAINT fk_stock_levels_part_id FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE RESTRICT
);
COMMENT ON TABLE stock_levels IS 'Остатки запчастей';

CREATE TABLE parts_usage (
    usage_id TEXT NOT NULL,
    ticket_id TEXT,
    part_id TEXT,
    quantity_used INTEGER,
    used_at DATE
    , CONSTRAINT pk_parts_usage PRIMARY KEY (usage_id)
    , CONSTRAINT fk_parts_usage_ticket_id FOREIGN KEY (ticket_id) REFERENCES service_tickets (ticket_id) ON DELETE RESTRICT
    , CONSTRAINT fk_parts_usage_part_id FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE RESTRICT
);
COMMENT ON TABLE parts_usage IS 'Расход запчастей по заявкам';

CREATE TABLE demand_forecast (
    forecast_id TEXT NOT NULL,
    part_id TEXT,
    period TEXT,
    predicted_qty INTEGER,
    based_on_failures INTEGER
    , CONSTRAINT pk_demand_forecast PRIMARY KEY (forecast_id)
    , CONSTRAINT fk_demand_forecast_part_id FOREIGN KEY (part_id) REFERENCES parts (part_id) ON DELETE RESTRICT
);
COMMENT ON TABLE demand_forecast IS 'Прогноз потребности в ЗИП';

-- ------------------------------------------------------------
-- Модуль 5. Аналитика и предиктивное обслуживание
-- ------------------------------------------------------------
CREATE TABLE failure_stats (
    stat_id TEXT NOT NULL,
    model_id TEXT,
    component_node TEXT,
    failure_count INTEGER,
    period TEXT
    , CONSTRAINT pk_failure_stats PRIMARY KEY (stat_id)
    , CONSTRAINT fk_failure_stats_model_id FOREIGN KEY (model_id) REFERENCES equipment_models (model_id) ON DELETE RESTRICT
);
COMMENT ON TABLE failure_stats IS 'Статистика отказов по узлам/моделям';

CREATE TABLE mtbf_records (
    record_id TEXT NOT NULL,
    equipment_id TEXT,
    mtbf_hours INTEGER,
    calculated_at DATE
    , CONSTRAINT pk_mtbf_records PRIMARY KEY (record_id)
    , CONSTRAINT fk_mtbf_records_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
);
COMMENT ON TABLE mtbf_records IS 'Наработка на отказ (MTBF)';

CREATE TABLE predictive_alerts (
    alert_id TEXT NOT NULL,
    equipment_id TEXT,
    risk_score NUMERIC(12,2),
    predicted_failure_date DATE,
    recommended_action TEXT,
    created_at DATE
    , CONSTRAINT pk_predictive_alerts PRIMARY KEY (alert_id)
    , CONSTRAINT fk_predictive_alerts_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
);
COMMENT ON TABLE predictive_alerts IS 'Предиктивные оповещения';

CREATE TABLE service_cost_by_client (
    record_id TEXT NOT NULL,
    client_id TEXT,
    period TEXT,
    total_cost NUMERIC(12,2),
    ticket_count INTEGER
    , CONSTRAINT pk_service_cost_by_client PRIMARY KEY (record_id)
    , CONSTRAINT fk_service_cost_by_client_client_id FOREIGN KEY (client_id) REFERENCES clients (client_id) ON DELETE RESTRICT
);
COMMENT ON TABLE service_cost_by_client IS 'Стоимость сервиса по клиенту';

-- ------------------------------------------------------------
-- Модуль 6. Обратная связь в R&D
-- ------------------------------------------------------------
CREATE TABLE claims (
    claim_id TEXT NOT NULL,
    ticket_id TEXT,
    equipment_id TEXT,
    component_node TEXT,
    defect_description TEXT,
    severity claims_severity_enum,
    status claims_status_enum,
    created_at DATE
    , CONSTRAINT pk_claims PRIMARY KEY (claim_id)
    , CONSTRAINT fk_claims_ticket_id FOREIGN KEY (ticket_id) REFERENCES service_tickets (ticket_id) ON DELETE RESTRICT
    , CONSTRAINT fk_claims_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE RESTRICT
);
COMMENT ON TABLE claims IS 'Рекламации / структурированные дефекты';

CREATE TABLE rd_reports (
    report_id TEXT NOT NULL,
    period TEXT,
    summary TEXT,
    created_at DATE
    , CONSTRAINT pk_rd_reports PRIMARY KEY (report_id)
);
COMMENT ON TABLE rd_reports IS 'Отчёты для R&D';

CREATE TABLE claim_report_link (
    claim_id TEXT NOT NULL,
    report_id TEXT NOT NULL
    , CONSTRAINT pk_claim_report_link PRIMARY KEY (claim_id, report_id)
    , CONSTRAINT fk_claim_report_link_claim_id FOREIGN KEY (claim_id) REFERENCES claims (claim_id) ON DELETE RESTRICT
    , CONSTRAINT fk_claim_report_link_report_id FOREIGN KEY (report_id) REFERENCES rd_reports (report_id) ON DELETE RESTRICT
);
COMMENT ON TABLE claim_report_link IS 'Связь рекламаций и отчётов (M:N)';

CREATE TABLE product_improvements (
    improvement_id TEXT NOT NULL,
    report_id TEXT,
    description TEXT,
    status product_improvements_status_enum,
    implemented_date DATE
    , CONSTRAINT pk_product_improvements PRIMARY KEY (improvement_id)
    , CONSTRAINT fk_product_improvements_report_id FOREIGN KEY (report_id) REFERENCES rd_reports (report_id) ON DELETE RESTRICT
);
COMMENT ON TABLE product_improvements IS 'Улучшения продукта';

COMMIT;
