INSERT INTO role (code, name)
VALUES ('USER', '普通用户'),
       ('ADMIN', '管理员');

INSERT INTO provider (code, name)
VALUES ('OPENAI', 'OpenAI');

INSERT INTO model (provider_id, code, display_name)
SELECT id, 'gpt-4.1-mini', 'GPT-4.1 Mini'
FROM provider
WHERE code = 'OPENAI';

INSERT INTO pricing_rule (model_id, input_token_price, output_token_price, currency)
SELECT id, 0.00000100, 0.00000400, 'CNY'
FROM model
WHERE code = 'gpt-4.1-mini';

INSERT INTO user_account (username,password_hash,email)
VALUES ('hdw','mock_password','example@test.com');

