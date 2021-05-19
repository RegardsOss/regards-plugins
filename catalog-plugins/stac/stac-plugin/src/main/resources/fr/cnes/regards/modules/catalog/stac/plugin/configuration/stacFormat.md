The STAC plugin features a mechanism to convert values back and forth between
the STAC standard format and the REGARDS property format.

For instance, the STAC format for cloud coverage is a number between 0 and 100 ("base 100").
There are other possibilities to format a percentage, namely as a floating point 
number between 0 and 1 ("base 1"). If a REGARDS property corresponds to the cloud coverage
percentage of an image in base 1, the value must be converted to base 100 when displayed
by the STAC plugin. 

Conversely, when the user sends queries formatted as STAC `/search` requests featuring a 
cloud coverage range, the range is expressed in base 100. Since, in this example, the cloud coverage 
in indexed in base 1 in Elasticsearch, the range value must then be converted to base 1 in 
order to make the request in Elasticsearch.

This explains why we need to be able to convert back and forth between the two representations.

For now, only the STAC properties of type `PERCENTAGE` support formatting conversion.

### `PERCENTAGE` STAC format

The STAC format property must be:
- `HUNDRED`: for percentages represented as numbers between 0 and 100
- `ONE`: for percentages represented as floating point numbers between 0 and 1

The default value is `HUNDRED`, so that this value does not need to be configured for the 
`eo:cloud_coverage` property.

