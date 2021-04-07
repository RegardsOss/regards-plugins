# STAC engine

## Description

The STAC engine protocol allows to browse the REGARDS catalog in the STAC standard format,
using the STAC API standard.

 **Official specifications :**
 - [STAC spec](https://github.com/radiantearth/stac-spec/tree/v1.0.0-beta.2)
 - [STAC API spec](https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1)

## Usage

 Parameters to configure :
 - **STAC title** : Title given to the main root catalog, defaults to "STAC catalog $PROJECT" 
                    where $PROJECT is the current project's name 
 - **STAC description** : description of the root catalog
 - **STAC root static collection title** : Displayed label for the static collections root
 - **STAC root dynamic collection title** : Displayed label for the dynamic collections root
 - **STAC datetime property** : Mandatory configuration for the datetime property, corresponding to the 'temporal' aspect of the STAC spec.
 - **STAC extra properties** : List of other STAC properties to be mapped to model attributes
 - **STAC dataset properties** : STAC collection properties for selected datasets, defining for instance license and providers for collections/datasets

## STAC Collection management

STAC collections are created from two different sources:
- static collections from REGARDS collections and datasets,
- dynamic collections from the configured list of STAC properties.

(If no dynamic collections are configured, the user see directly the 
available static collections. Otherwise, the user is given the choice
between static and dynamic collections as two separate collections.)

### Static collections

In the STAC standard, STAC collections are meant to hold a rather small number
of items, in the hundreds at most. This is why, if the catalog contains a lot 
of items, it is a good idea to split them in many different collections. 

However, REGARDS already has the notion of collection/datasets, so we recreate
a tree of STAC collections from them.

REGARDS Collections can reference other collections, allowing to build a tree
of collections. Similarly, datasets can reference collections. This is used
by the STAC static collection mechanism to build a tree of STAC collections.

At the root level, static collections display links to the collections or datasets
with no parent collection (referencing no other REGARDS collection).

A user navigating to a STAC collection made from a REGARDS collection will see:
- if this collection is referenced by other collections/datasets: links to these collections/datasets,
- if this collection has only items, the link to the list of items.

A user navigating to a STAC collection made from a REGARDS dataset will see:
- the link to the list of items in this dataset.

### Dynamic collections

The user may choose to select some of the configured STAC properties 
(even the mandatory datetime property) as levels in a dynamic tree.

Each level has at least one sublevel, but may have more. 
For instance, datetime properties may have several sublevel consisting of
selecting the year, then the month, then the day.

The dynamic collection is entirely configured in the plugin configuration,
thanks to the "dynamic collection level" and "dynamic collection format"
parameters of the STAC properties. The documentation for these parameters
explains in details how to set their values correctly.
