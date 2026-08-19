create table record_image_objects (
    record_image_object_id bigint not null auto_increment,
    owner_user_id bigint not null,
    object_key varchar(500) not null,
    status varchar(30) not null,
    cleanup_claim_token varchar(36),
    cleanup_claimed_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (record_image_object_id),
    constraint uk_record_image_objects_object_key unique (object_key),
    index idx_record_image_objects_status (status)
) engine=InnoDB;

insert into record_image_objects (
    owner_user_id,
    object_key,
    status,
    created_at,
    updated_at
)
select records.user_id,
       images.object_key,
       'ATTACHED',
       images.created_at,
       images.created_at
from daily_record_images images
join daily_records records
  on records.daily_record_id = images.daily_record_id;
