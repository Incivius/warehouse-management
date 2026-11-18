
INSERT INTO app_user (
    name,
    login,
    password_hash,
    created_at
) VALUES
(
    'Administrador',
    'admin',
    '$2a$10$Lg7m7t0KGnL9eGpq/.FnsuJICOaGDrYt2iYHVeA5sQu2lhop9vWtW',
    CURRENT_TIMESTAMP
),
(
    'Operador 1',
    'operador1',
    '$2a$10$ivjpsIf2IpE4Xf1VAI/53OlySr0XTYRJ3qMkAEuKZ8Zkaggax35ce',
    CURRENT_TIMESTAMP
),
(
    'Operador 2',
    'operador2',
    '$2a$10$dOksB4Fr5NuznylLm3bZJ.8tY3INcdp.V.px6twjF.cEHer0dVnme',
    CURRENT_TIMESTAMP
);
