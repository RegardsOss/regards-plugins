> Positive integer, leave blank or set as negative value to ignore.

This value defines the level of the STAC property in the dynamic collections.

### Format

This parameter's value should be set to a positive integer.

Negative values can be used to explicitly ignore the property.

### Default value

The default value is -1 (a negative value).

When the value is negative or left unset, the STAC property is ignored in 
the generation of the dynamic collection levels.

### Unique values

Each STAC property having a dynamic collection level set should be set to a different positive integer.

If several STAC properties have the same level, the order of the properties in the dynamic collections
is not guaranteed.
