-- 룸메이트 생필품 소진예측 + 정산 시스템 DB 생성 스크립트
-- 프로그램을 처음 실행하기 전에 MySQL에서 한 번만 실행한다.

create database if not exists roommate default character set utf8mb4 collate utf8mb4_bin;

use roommate;

create table if not exists member (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  primary key (id),
  unique key name_UNIQUE (name)
);

create table if not exists item (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  primary key (id),
  unique key name_UNIQUE (name)
);

create table if not exists purchase (
  id int NOT NULL AUTO_INCREMENT,
  item_name varchar(255) NOT NULL,
  buyer varchar(255) NOT NULL,
  cost int NOT NULL,
  purchase_date date NOT NULL,
  primary key (id)
);