# umlaut-mysql-ddl

Generate (mysql)ddl from [umlaut](https://github.com/workco/umlaut) dsl files.

## API

- Pass in a path to an umlaut file to `umlaut.generators.ddl.mysql/gen`.
- Returns a seq of maps containing `:create` and `:drop` keys where the values are the ddl to create and drop tables, respectively.

```clj
(require '[umlaut.generators.ddl.mysql :refer [gen]])
(gen "path/to/awesome/datamodel.umlaut")
=> ({:drop "DROP TABLE lisper" :create "CREATE TABLE lisper....."}
    ...)
```

## Annotations

### Type Annotation

A type level annotation with `lang/ddl` namespace will cause ddl to be emitted for that type. Nothing is emitted if this annotation is missing.

### Field Annotations

- `@lang/ddl primary_key _` - Indicates the corresponding column is a primary key. If multiple fields have this annotation, a composite primary key will be emitted.
- `@lang/ddl unique _` - Indicates the corresponding column has a unique constraint. If multiple fields have this annotation, a composite unique constraint will be emitted.
- `@lang/ddl type <THE TYPE>` - Overrites the column's datatype with specified value.
- `@lang/ddl type <THE TYPE>.<PARAMETER>` - Overrites the column's datatype with specified value with a parameter. e.g. `@lang/ddl type varchar.50`
- `@lang/ddl fk <OTHER TYPE>.<FIELD>` - Adds a foreign key constraint
- `@lang/ddl AUTO_INCREMENT true` - Indicates that column should be auto incremented
- `@lang/ddl supress _` - Indicates that a column should not be emitted for this field. Useful when e.g. you want to expose a field that is not sourced as a column of a table.
- `@doc "<the comment>"` - renders a comment for the corresponding column

## Mapping with umlaut built in dsl

### Enum

Umlaut enums map to enum columns.

### Not null

A field type that is not optional e.g. `String` (instead of `String?`) adds a not null constraint to the corresponding column.

### Default type mapping

It is encouraged to be very specific about the datatype of the column using the `@lang/ddl type` annotation. Having that said, below is the default mapping.

```
 ;;umlaut     ;;ddl
{"ID"         "BIGINT"
 "String"     "TEXT"
 "Integer"    "INT"}
```

### List field type

While this does not directly affect the rendered ddl, it adds information for sorting the tables topologically,

If a type Foo has field with a list a type, e.g.

```
@lang/ddl _ _
type Foo {
  someField: Bar[0..n]?`
}
```

Then the ddl generator infers that table `foo` should be created before table `bar` and performs a topological sort to determine the order of the emitted ddl.

## Migrations?

This generator does not handle migrations, and is a simple mapping of a umlaut datamodel to ddl.
Having that said, a [tool to compare the schema equality between 2 database instances](https://github.com/xcoo/mysql-ddl-diff) had been developed, which can be used to ensure that the 2 databases - one created from the generated ddl, and the other from the migrations - have exactly the same schema. Check it out!

## License

Copyright [Xcoo, Inc.][xcoo]

Licensed under the [Apache License, Version 2.0][apache-license-2.0].

[xcoo]: https://xcoo.jp/
[apache-license-2.0]: http://www.apache.org/licenses/LICENSE-2.0.html
