For dynamic collections, use this parameter to define the format of the dynamic collection.

The formats are:
- for datetimes: `YEAR` | `MONTH` | `DAY` | `HOUR` | `MINUTE`
- for numbers: `MIN;INTERVAL;MAX` 
  (for instance `5;10;95` for percentages)
- for string prefixes: `PREFIX(X,Y)` where `X` is a number and `Y` is `A`, `9` or `A9` 
  (for instance `PREFIX(3,A9)`) 

### Applicability

This parameter is only applicable to the following STAC property types:
- STRING,
- NUMBER and other number-based types (ANGLE, LENGTH, PERCENTAGE),
- DATETIME.

For other STAC property types, it is treated as if it was valued with "EXACT".

### 'EXACT' value

When this property has the value "EXACT", it means that we want to sub-categorize on the
exact values that the property takes in the applicable documents.

> For instance, the STAC property we are currently configuring is `satellite`,
and there are only two values for this property in the documents: `SPOT5` and `Sentinel-2`.
>
> In this case, this property will be treated as a dynamic collection with a level
> giving access to two sublevels:
> - `satellite=SPOT5`
> - `satellite=Sentinel-2`

#### WARNING: use with care

Although this is the default configuration, administrators must be warned
that, due to the lack of pagination in the STAC collections standard, using
"EXACT" levels must be used only when the number of values for the property is low:
a few dozens at most.

### Datetime properties

Datetime properties must be configured with one of the following values:
- `YEAR`
- `MONTH`
- `DAY` (default value)
- `HOUR`
- `MINUTE`

This will provide sublevels going to the configured one.

> For instance, if the administrator configures the `datetime` property to have `HOUR` as
the STAC dynamic collection format, then the dynamic collections will have three
sublevels:
>
> - the year sublevel, allowing to choose the year (only the applicable year range is proposed), 
>  for instance:
>  - `datetime=2020`
>  - `datetime=2021`
> - the month sublevel, allowing to choose the month (all the months are proposed),
>  for instance:
>  - `datetime=2021-01`
>  - `datetime=2021-02`
>  - ...
>  - `datetime=2021-12`
> - the day sublevel, allowing to choose the day of the month (all the days of that month are proposed),
>  for instance:
>  - `datetime=2021-02-01`
>  - `datetime=2021-02-02`
>  - ...
>  - `datetime=2021-02-28`
> - and finally the hour sublevel, allowing to choose the hour of the day (all the hours of that month are proposed),
>  for instance:
>  - `datetime=2021-02-07T00`
>  - `datetime=2021-02-07T01`
>  - ...
>  - `datetime=2021-02-07T23`

### Number properties

The number properties can be configured with a `MIN;INTERVAL;MAX` value, separated by semi-colons.

This will create a dynamic sublevel collection for:
- everything under the minimum,
- one collection per interval (starting at minimum to minimum plus interval, up until we reach the maximum)
- everything over the maximum.

> For instance, for the `eo:cloud_coverage` STAC property, the administrator configures
> it with the value: `5;10;95`.
> 
> This creates a dynamic collection level with the following sub-collections:
> - `eo:cloud_coverage < 5`
> - `5 < eo:cloud_coverage < 15`
> - `15 < eo:cloud_coverage < 25` 
> - ...
> - `85 < eo:cloud_coverage < 95` 
> - `eo:cloud_coverage > 95` 

#### WARNING: choose `INTERVAL` with care

The administrator must provide a configuration that does not contain too many intervals,
at most a few dozen.

If the `INTERVAL` given is a lot smaller than `MAX - MIN`, this will generate many
subcollections, and the lack of STAC collection standard for pagination will result
in a very long list of possibilities.

The user's browser/client may receive a very big response with too many links to be useful.

### String properties

String properties can be configured with a set of prefix letters/numbers.

The format is `PREFIX(X,Y)` where:
- `X` is the size of the prefix in number of characters (for instance `3`),
- `Y` is the type of characters to consider, it can be:
  - `A` for only alphabetic characters (A to Z),
  - `9` for only digits (0 to 9),
  - `A9` for both alphabetic characters and digits.

> For instance, the administrator configures the property `country` to be `PREFIX(2,A)`.
>
> This creates a dynamic collection level with two sublevels:
> - first choosing the first letter:
>   - `country=A...`
>   - `country=B...`
>   - ...
>   - `country=Z...` 
> - then choosing the second letter:
>   - `country=FA...`
>   - `country=FB...`
>   - ...
>   - `country=FZ...` 

