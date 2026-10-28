-- Schema as it existed before loans: users, their roles and refresh tokens.

create table app_users (
    id             uuid primary key,
    email          varchar(254)             not null unique,
    first_name     varchar(100),
    last_name      varchar(100),
    display_name   varchar(150),
    avatar_url     varchar(2048),
    logto_subject  varchar(255) unique,
    email_verified boolean                  not null default false,
    status         varchar(20)              not null,
    created_at     timestamp(6) with time zone not null,
    updated_at     timestamp(6) with time zone not null
);

create table app_user_roles (
    user_id uuid        not null references app_users (id) on delete cascade,
    role    varchar(30) not null,
    primary key (user_id, role)
);

create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid                        not null references app_users (id) on delete cascade,
    token_hash varchar(64)                 not null unique,
    expires_at timestamp(6) with time zone not null,
    revoked_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);
