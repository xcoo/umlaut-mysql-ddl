CREATE TABLE lisper (
   lisp ENUM('COMMON_LISP','CLOJURE','SCHEME') NOT NULL    ,
   id BIGINT NOT NULL   AUTO_INCREMENT comment 'Uniquely identifies a lisper',
   full_name varchar(50) NOT NULL    ,
   employed boolean NOT NULL  DEFAULT true   ,
   PRIMARY KEY(id),
   UNIQUE INDEX unique_full_name(full_name),
   INDEX index_employed(employed))
ENGINE = InnoDB

CREATE TABLE company (
   id BIGINT NOT NULL    ,
   name varchar(50) NOT NULL    ,
   address varchar(50) NOT NULL    ,
   PRIMARY KEY(id),
   UNIQUE INDEX unique_name_address(name,address))
ENGINE = InnoDB

CREATE TABLE job (
   lisper_id BIGINT NOT NULL    ,
   company_id BIGINT NOT NULL    ,
   salary BIGINT NOT NULL    ,
   description varchar(255) NOT NULL    ,
   PRIMARY KEY(lisper_id,company_id),
   CONSTRAINT `fk_job_lisper` FOREIGN KEY (lisper_id) REFERENCES lisper (id) ,
   CONSTRAINT `fk_job_company` FOREIGN KEY (company_id) REFERENCES company (id) )
ENGINE = InnoDB