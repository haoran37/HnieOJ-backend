-- Apply once to existing databases after deploying the matching submission service.
ALTER TABLE `hnieoj_judge_db`.`judge`
    ADD INDEX `idx_hid_status_create` (`hid`, `status`, `gmt_create`);
