create table cosmetic_tags (
    tag_order integer not null,
    cosmetic_product_id bigint not null,
    cosmetic_tag_id bigint not null auto_increment,
    tag_type varchar(20) not null,
    tag_name varchar(100) not null,
    primary key (cosmetic_tag_id),
    constraint uk_cosmetic_tags_product_type_order
        unique (cosmetic_product_id, tag_type, tag_order),
    constraint fk_cosmetic_tags_product
        foreign key (cosmetic_product_id)
        references cosmetic_products (cosmetic_product_id)
) engine=InnoDB;
