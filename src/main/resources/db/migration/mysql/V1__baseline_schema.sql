
    create table anonymous_sessions (
        anonymous_session_id bigint not null auto_increment,
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        last_accessed_at datetime(6),
        user_id bigint not null,
        token_hash varchar(64) not null,
        primary key (anonymous_session_id)
    ) engine=InnoDB;

    create table cosmetic_ingredients (
        ingredient_order integer not null,
        is_main bit not null,
        cosmetic_ingredient_id bigint not null auto_increment,
        cosmetic_product_id bigint not null,
        ingredient_id bigint,
        ingredient_name varchar(100) not null,
        primary key (cosmetic_ingredient_id)
    ) engine=InnoDB;

    create table cosmetic_products (
        cosmetic_product_id bigint not null auto_increment,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        updated_at datetime(6) not null,
        source_type varchar(20) not null,
        product_type varchar(50) not null,
        brand_name varchar(100),
        product_name varchar(255) not null,
        source_key varchar(255),
        image_url TEXT,
        primary key (cosmetic_product_id)
    ) engine=InnoDB;

    create table cosmetic_set_items (
        item_order integer not null,
        cosmetic_set_id bigint not null,
        cosmetic_set_item_id bigint not null auto_increment,
        created_at datetime(6) not null,
        user_cosmetic_id bigint not null,
        primary key (cosmetic_set_item_id)
    ) engine=InnoDB;

    create table cosmetic_sets (
        cosmetic_set_id bigint not null auto_increment,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        user_id bigint not null,
        name varchar(100) not null,
        usage_time enum ('BOTH','MORNING','NIGHT') not null,
        primary key (cosmetic_set_id)
    ) engine=InnoDB;

    create table daily_health_summaries (
        average_heart_rate integer,
        body_water_mass_kg decimal(5,2),
        exercise_minutes integer,
        exercise_performed bit,
        hrv_rmssd decimal(7,2),
        hydration_intake_ml integer,
        max_heart_rate integer,
        resting_heart_rate integer,
        skin_temperature_celsius decimal(4,2),
        sleep_minutes integer,
        steps integer,
        summary_date date not null,
        calculated_at datetime(6) not null,
        daily_health_summary_id bigint not null auto_increment,
        user_id bigint not null,
        stress_level varchar(20),
        menstruation_status varchar(30),
        primary key (daily_health_summary_id)
    ) engine=InnoDB;

    create table daily_record_cosmetics (
        created_at datetime(6) not null,
        daily_record_cosmetic_id bigint not null auto_increment,
        daily_record_id bigint not null,
        user_cosmetic_id bigint not null,
        usage_period varchar(10) not null,
        product_type_snapshot varchar(50) not null,
        brand_name_snapshot varchar(100),
        custom_name_snapshot varchar(100),
        ingredients_snapshot json,
        product_name_snapshot varchar(255) not null,
        primary key (daily_record_cosmetic_id)
    ) engine=InnoDB;

    create table daily_record_images (
        sort_order integer not null,
        created_at datetime(6) not null,
        daily_record_id bigint not null,
        daily_record_image_id bigint not null auto_increment,
        file_size bigint not null,
        mime_type varchar(50) not null,
        object_key varchar(500) not null,
        primary key (daily_record_image_id)
    ) engine=InnoDB;

    create table daily_records (
        record_date date not null,
        created_at datetime(6) not null,
        daily_record_id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        skin_status varchar(20) not null,
        food_memo varchar(300),
        memo varchar(300),
        primary key (daily_record_id)
    ) engine=InnoDB;

    create table daily_reports (
        created_at datetime(6) not null,
        daily_health_summary_id bigint,
        daily_record_id bigint not null,
        daily_report_id bigint not null auto_increment,
        generated_at datetime(6),
        regenerated_at datetime(6),
        updated_at datetime(6) not null,
        report_status varchar(20) not null,
        note varchar(300),
        ai_analysis TEXT,
        ai_summary TEXT,
        personalized_solution TEXT,
        primary key (daily_report_id)
    ) engine=InnoDB;

    create table health_connections (
        connected_at datetime(6) not null,
        health_connection_id bigint not null auto_increment,
        last_synced_at datetime(6),
        revoked_at datetime(6),
        user_id bigint not null,
        device_token_hash varchar(64) not null,
        device_id varchar(255) not null,
        primary key (health_connection_id)
    ) engine=InnoDB;

    create table health_raw_records (
        created_at datetime(6) not null,
        end_time datetime(6),
        health_connection_id bigint not null,
        health_raw_record_id bigint not null auto_increment,
        start_time datetime(6) not null,
        updated_at datetime(6) not null,
        record_type varchar(50) not null,
        hc_record_id varchar(255) not null,
        payload json not null,
        primary key (health_raw_record_id)
    ) engine=InnoDB;

    create table ingredients (
        created_at datetime(6) not null,
        ingredient_id bigint not null auto_increment,
        name varchar(100) not null,
        primary key (ingredient_id)
    ) engine=InnoDB;

    create table push_tokens (
        created_at datetime(6) not null,
        push_token_id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        token varchar(512) not null,
        device_id varchar(255) not null,
        primary key (push_token_id)
    ) engine=InnoDB;

    create table skin_care_tips (
        is_active bit not null,
        created_at datetime(6) not null,
        skin_care_tip_id bigint not null auto_increment,
        updated_at datetime(6) not null,
        content varchar(500) not null,
        primary key (skin_care_tip_id)
    ) engine=InnoDB;

    create table user_consents (
        agreed bit not null,
        agreed_at datetime(6) not null,
        consent_id bigint not null auto_increment,
        created_at datetime(6) not null,
        user_id bigint not null,
        version varchar(20) not null,
        consent_type varchar(50) not null,
        primary key (consent_id)
    ) engine=InnoDB;

    create table user_cosmetics (
        cosmetic_product_id bigint not null,
        created_at datetime(6) not null,
        deleted_at datetime(6),
        updated_at datetime(6) not null,
        user_cosmetic_id bigint not null auto_increment,
        user_id bigint not null,
        custom_name varchar(100),
        primary key (user_cosmetic_id)
    ) engine=InnoDB;

    create table user_daily_skin_care_tips (
        tip_date date not null,
        created_at datetime(6) not null,
        skin_care_tip_id bigint not null,
        user_daily_tip_id bigint not null auto_increment,
        user_id bigint not null,
        primary key (user_daily_tip_id)
    ) engine=InnoDB;

    create table user_notification_settings (
        daily_report_enabled bit not null,
        record_reminder_enabled bit not null,
        record_reminder_time time(0),
        weekly_report_enabled bit not null,
        weekly_report_time time(0),
        created_at datetime(6) not null,
        notification_setting_id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        primary key (notification_setting_id)
    ) engine=InnoDB;

    create table users (
        created_at datetime(6) not null,
        profile_completed_at datetime(6),
        updated_at datetime(6) not null,
        user_id bigint not null auto_increment,
        age_group varchar(10),
        gender varchar(10),
        nickname varchar(10),
        skin_type varchar(30),
        primary key (user_id)
    ) engine=InnoDB;

    create table weekly_report_analyses (
        sort_order integer not null,
        weekly_report_analysis_id bigint not null auto_increment,
        weekly_report_id bigint not null,
        content TEXT not null,
        primary key (weekly_report_analysis_id)
    ) engine=InnoDB;

    create table weekly_reports (
        week_end_date date not null,
        week_start_date date not null,
        created_at datetime(6) not null,
        generated_at datetime(6) not null,
        regenerated_at datetime(6),
        updated_at datetime(6) not null,
        user_id bigint not null,
        weekly_report_id bigint not null auto_increment,
        note varchar(300),
        personalized_solution TEXT not null,
        weekly_summary TEXT not null,
        primary key (weekly_report_id)
    ) engine=InnoDB;

    alter table anonymous_sessions 
       add constraint uk_anonymous_sessions_token_hash unique (token_hash);

    alter table cosmetic_ingredients 
       add constraint uk_cosmetic_ingredients_product_order unique (cosmetic_product_id, ingredient_order);

    create index idx_cosmetic_products_product_name 
       on cosmetic_products (product_name);

    alter table cosmetic_set_items 
       add constraint uk_cosmetic_set_items_set_cosmetic unique (cosmetic_set_id, user_cosmetic_id);

    alter table cosmetic_set_items 
       add constraint uk_cosmetic_set_items_set_order unique (cosmetic_set_id, item_order);

    create index idx_daily_health_summaries_summary_date 
       on daily_health_summaries (summary_date);

    alter table daily_health_summaries 
       add constraint uk_daily_health_summaries_user_date unique (user_id, summary_date);

    alter table daily_record_images 
       add constraint uk_daily_record_images_object_key unique (object_key);

    alter table daily_record_images 
       add constraint uk_daily_record_images_record_sort unique (daily_record_id, sort_order);

    alter table daily_records 
       add constraint uk_daily_records_user_date unique (user_id, record_date);

    alter table daily_reports 
       add constraint uk_daily_reports_record_id unique (daily_record_id);

    alter table daily_reports 
       add constraint uk_daily_reports_health_summary_id unique (daily_health_summary_id);

    alter table health_connections 
       add constraint uk_health_connections_user_device unique (user_id, device_id);

    alter table health_connections 
       add constraint uk_health_connections_device_token_hash unique (device_token_hash);

    create index idx_health_raw_records_record_type 
       on health_raw_records (record_type);

    create index idx_health_raw_records_start_time 
       on health_raw_records (start_time);

    alter table health_raw_records 
       add constraint uk_health_raw_records_connection_record unique (health_connection_id, hc_record_id);

    alter table ingredients 
       add constraint uk_ingredients_name unique (name);

    alter table push_tokens 
       add constraint uk_push_tokens_user_device unique (user_id, device_id);

    alter table push_tokens 
       add constraint uk_push_tokens_token unique (token);

    alter table user_consents 
       add constraint uk_user_consents_user_type_version unique (user_id, consent_type, version);

    create index idx_user_cosmetics_user_deleted 
       on user_cosmetics (user_id, deleted_at);

    alter table user_daily_skin_care_tips 
       add constraint uk_user_daily_skin_care_tips_user_date unique (user_id, tip_date);

    alter table user_notification_settings 
       add constraint uk_user_notification_settings_user unique (user_id);

    alter table weekly_report_analyses 
       add constraint uk_weekly_report_analyses_report_sort unique (weekly_report_id, sort_order);

    alter table weekly_reports 
       add constraint uk_weekly_reports_user_start unique (user_id, week_start_date);

    alter table anonymous_sessions 
       add constraint FKq8291s5589tkwyt3d2o63h0xb 
       foreign key (user_id) 
       references users (user_id);

    alter table cosmetic_ingredients 
       add constraint FK83isb85ytr6cxcxbk6yk5snnd 
       foreign key (cosmetic_product_id) 
       references cosmetic_products (cosmetic_product_id);

    alter table cosmetic_ingredients 
       add constraint FKl0weuql6iieuvwuastnnkdl28 
       foreign key (ingredient_id) 
       references ingredients (ingredient_id);

    alter table cosmetic_products 
       add constraint FKa3cdtrle8x710bmcmg53sy8op 
       foreign key (created_by_user_id) 
       references users (user_id);

    alter table cosmetic_set_items 
       add constraint FKlmmmgkhy28x7ryo157fga76n2 
       foreign key (cosmetic_set_id) 
       references cosmetic_sets (cosmetic_set_id);

    alter table cosmetic_set_items 
       add constraint FKp28wmrqyxh2lnhbfhxhchsogk 
       foreign key (user_cosmetic_id) 
       references user_cosmetics (user_cosmetic_id);

    alter table cosmetic_sets 
       add constraint FKblxmy4oynk4apntlsd6rkxixo 
       foreign key (user_id) 
       references users (user_id);

    alter table daily_health_summaries 
       add constraint FKsowb09yd4tlvvdai5yoi372ce 
       foreign key (user_id) 
       references users (user_id);

    alter table daily_record_cosmetics 
       add constraint FKpuyp702bxraoftkg9dna076cq 
       foreign key (daily_record_id) 
       references daily_records (daily_record_id);

    alter table daily_record_cosmetics 
       add constraint FKwhvihgrfdi40ayd0ll10kp6n 
       foreign key (user_cosmetic_id) 
       references user_cosmetics (user_cosmetic_id);

    alter table daily_record_images 
       add constraint FK811q4p0pf3uj6e03u55qwpg3q 
       foreign key (daily_record_id) 
       references daily_records (daily_record_id);

    alter table daily_records 
       add constraint FK8lraprtn3xr7kik4ir2w3k31a 
       foreign key (user_id) 
       references users (user_id);

    alter table daily_reports 
       add constraint FKnjqovgog8skom2a11itra6w5o 
       foreign key (daily_health_summary_id) 
       references daily_health_summaries (daily_health_summary_id);

    alter table daily_reports 
       add constraint FK1fpdp6lmjfnkjhebysmd2xeou 
       foreign key (daily_record_id) 
       references daily_records (daily_record_id);

    alter table health_connections 
       add constraint FKi7b42ifkbs4vkitoao30khe49 
       foreign key (user_id) 
       references users (user_id);

    alter table health_raw_records 
       add constraint FKkhcgwe3o5n23nyms8lhh3m98f 
       foreign key (health_connection_id) 
       references health_connections (health_connection_id);

    alter table push_tokens 
       add constraint FKgisqbur2nbpemhidpyqv501nd 
       foreign key (user_id) 
       references users (user_id);

    alter table user_consents 
       add constraint FK2jrdhofjcd44quyuq5x9wlb8b 
       foreign key (user_id) 
       references users (user_id);

    alter table user_cosmetics 
       add constraint FKjle9vq37aiweollrgltomouv9 
       foreign key (cosmetic_product_id) 
       references cosmetic_products (cosmetic_product_id);

    alter table user_cosmetics 
       add constraint FKl9jghobetthqkui3h1tjtrnx8 
       foreign key (user_id) 
       references users (user_id);

    alter table user_daily_skin_care_tips 
       add constraint FK4runk1ejenu3cq4yvn52ni1c9 
       foreign key (skin_care_tip_id) 
       references skin_care_tips (skin_care_tip_id);

    alter table user_daily_skin_care_tips 
       add constraint FKs3r7xwei852685w4b9a7y3ekp 
       foreign key (user_id) 
       references users (user_id);

    alter table user_notification_settings 
       add constraint FKs9tjvxu8ko31ivjlq9l9duh9y 
       foreign key (user_id) 
       references users (user_id);

    alter table weekly_report_analyses 
       add constraint FK551dneaoiocbmtcyj64o5c9d8 
       foreign key (weekly_report_id) 
       references weekly_reports (weekly_report_id);

    alter table weekly_reports 
       add constraint FKqmbqcc7qb4mfr8wbc2xecqwin 
       foreign key (user_id) 
       references users (user_id);
