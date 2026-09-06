-- ---------------------------------------------------------------------------
-- WattWise — V2: seed appliance catalog with typical average consumption values
-- These are the per-type defaults used when a user creates an appliance without
-- supplying manual consumption figures. Values are representative of typical
-- Spanish household appliances (2023-2025).
-- ---------------------------------------------------------------------------

MERGE INTO appliance_catalog AS target
USING (VALUES
    ('WASHING_MACHINE', 2000, 1.000, 120, 'Lavadora — ciclo estándar 40-60ºC'),
    ('DISHWASHER',       1800, 0.900, 90,  'Lavavajillas — ciclo normal eco'),
    ('EV_CHARGER',       7400, 7.000, 240, 'Cargador vehículo eléctrico — carga doméstica'),
    ('DRYER',            2500, 2.000, 90,  'Secadora de tambor'),
    ('POOL_PUMP',        1100, 5.000, 300, 'Bomba de piscina'),
    ('AC',               2200, 1.500, 120, 'Aire acondicionado / bomba de calor'),
    ('OTHER',            1500, 1.200, 90,  'Genérico — consumos definidos por el usuario')
) AS source(type, avg_power_watts, avg_cycle_kwh, avg_cycle_minutes, description)
ON target.type = source.type
WHEN MATCHED THEN
    UPDATE SET avg_power_watts = source.avg_power_watts,
               avg_cycle_kwh   = source.avg_cycle_kwh,
               avg_cycle_minutes = source.avg_cycle_minutes,
               description     = source.description
WHEN NOT MATCHED THEN
    INSERT (type, avg_power_watts, avg_cycle_kwh, avg_cycle_minutes, description)
    VALUES (source.type, source.avg_power_watts, source.avg_cycle_kwh, source.avg_cycle_minutes, source.description);
