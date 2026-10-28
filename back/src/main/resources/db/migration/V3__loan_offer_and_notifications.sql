-- Conditions fixed at approval: effective annual rate (EA, %) and the resulting repayment figures.
alter table loans add column annual_rate     numeric(5, 2) check (annual_rate between 0 and 100);
alter table loans add column monthly_payment numeric(15, 2);
alter table loans add column total_payable   numeric(15, 2);
alter table loans add column total_interest  numeric(15, 2);

-- In-app messages for users (e.g. "your loan was approved at 24 % EA, installment ..."). Deleted with the user.
create table notifications (
    id         uuid primary key,
    user_id    uuid                        not null references app_users (id) on delete cascade,
    loan_id    uuid references loans (id),
    type       varchar(30)                 not null,
    title      varchar(150)                not null,
    message    varchar(1000)               not null,
    read_at    timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index idx_notifications_user on notifications (user_id, created_at desc);
create index idx_notifications_unread on notifications (user_id) where read_at is null;
