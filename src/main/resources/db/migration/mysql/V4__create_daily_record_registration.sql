alter table daily_record_cosmetics
    add column sort_order integer not null default 0;

update daily_record_cosmetics target
join (
    select ranked.daily_record_cosmetic_id,
           ranked.calculated_sort_order
    from (
        select daily_record_cosmetic_id,
               row_number() over (
                   partition by daily_record_id, usage_period
                   order by daily_record_cosmetic_id
               ) as calculated_sort_order
        from daily_record_cosmetics
    ) ranked
) ordered
    on target.daily_record_cosmetic_id = ordered.daily_record_cosmetic_id
set target.sort_order = ordered.calculated_sort_order;

alter table daily_record_cosmetics
    add constraint uk_daily_record_cosmetics_user_period
        unique (daily_record_id, user_cosmetic_id, usage_period);

alter table daily_record_cosmetics
    add constraint uk_daily_record_cosmetics_sort
        unique (daily_record_id, usage_period, sort_order);

create table daily_record_cosmetic_sets (
    sort_order integer not null,
    created_at datetime(6) not null,
    daily_record_cosmetic_set_id bigint not null auto_increment,
    daily_record_id bigint not null,
    source_cosmetic_set_id bigint not null,
    set_name_snapshot varchar(100) not null,
    set_usage_time_snapshot varchar(20) not null,
    usage_period varchar(10) not null,
    primary key (daily_record_cosmetic_set_id),
    constraint uk_daily_record_sets_source_period
        unique (daily_record_id, source_cosmetic_set_id, usage_period),
    constraint uk_daily_record_sets_sort
        unique (daily_record_id, usage_period, sort_order),
    constraint fk_daily_record_cosmetic_sets_record
        foreign key (daily_record_id) references daily_records (daily_record_id)
) engine=InnoDB;
