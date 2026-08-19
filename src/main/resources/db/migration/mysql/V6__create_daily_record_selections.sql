create table daily_record_selections (
    sort_order integer not null,
    created_at datetime(6) not null,
    daily_record_id bigint not null,
    daily_record_selection_id bigint not null auto_increment,
    source_id bigint not null,
    name_snapshot varchar(255) not null,
    selection_type varchar(10) not null,
    tags_snapshot json not null,
    usage_period varchar(10) not null,
    primary key (daily_record_selection_id),
    constraint uk_daily_record_selections_source
        unique (daily_record_id, usage_period, selection_type, source_id),
    constraint uk_daily_record_selections_sort
        unique (daily_record_id, usage_period, selection_type, sort_order),
    constraint fk_daily_record_selections_record
        foreign key (daily_record_id) references daily_records (daily_record_id)
) engine=InnoDB;
