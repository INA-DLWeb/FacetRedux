# FacetRedux


## A proprioception tool for real-time big data exploration.


FacetRedux is a tool developed and used at [Ina's Web Legal Deposit](http://www.institut-national-audiovisuel.fr/collecte-depot-legal-web.html) for crawl metadata data-mining. This tool enables us to explore statistical indicators about our archive in real-time. We use it on a day-to-day basis to have an overview of the contents we archive, in order to tune our collection tools and methods.

### Propriocetion ?

Proprioception is a concept we borrowed to [cognitive science](http://en.wikipedia.org/wiki/Cognitive_science), where it refers to the [intuitive perception we have of our own body](http://en.wikipedia.org/wiki/Proprioception). Proprioception explains how we are able to touch our nose without poking oursleves in the eyes : we constantly have an intuitive knowledge of the position our our and and nose.

This concept seemed relevent to us to describe our Web archive data-mining effort. As a matter of fact, one of the major challenges for a Web archive is to provide an analytical overview. On one hand, it is easy to request one of the billion documents from our archive, on the other hand, getting and answer to the following types of questions is more difficult:
 * "How did the number of unique images collected on arte.tv evolve from April 2012 to April 2013?"
 * "What is the average size of a Flash animation collected in 2013?"
 * "What is the number of unique URLs collected in the `.fr` TLD in 2013?"

### Mining metadata

Our Web archive is stored in DAFF files (Digital Archive File Format). When we archive a document (image, web page, style sheetc, etc.) we create two different records that we store separately:
 * a **data** record (document itself, document's [SHA-256 signature](http://en.wikipedia.org/wiki/SHA-2))
 * a **metadata** record, which describes the document (URL, date, SHA-256 signature, size, content type, etc.)

These two records share the document's SHA-256 signature. If the exact same content is archived twice, two *metadata* records with the same SHA-256 signature and different dates will be created, but only one *data* record will be stored.

Metadata records much lighter than data records since they do not store the content itself, but they contain enough information about the content to make them the ideal target for data-mining. This is the extensive list of information that is stored in our metadata records:
 * response status (`ok`, `redirection`, `error`, etc.)
 * URL
 * date
 * SHA-256 signature
 * MIME content-type (as found in the HTTP response headers)
 * client User-Agent (as found in the HTTP request headers)
 * size in bytes
 * archiving session identifier (site id + session start date)
 * depth (in hyperlinks, relative to site home page)

### Responding to generic questions

With the information available in our metadata records, we have built a system that is able to answer a particular kind of generic question. To do so, we have extracted from the metadata what we call **criteria** and **features**.
**Features** are numerical indicators that compute to answer a given request:
 1. number of matching metadata records
 2. number of *unique documents* (in the matching metadata records)
 3. number of *unique URLs* (in the matching metadata records)
 4. cumulated documents size in bytes (in the matching metadata records)
 5. cumulated *unique documents* size in bytes (in the matching metadata records)
 6. average document size (feature 5 / feature 3)

These **features** are requested for a set of **criteria** that are used to specify and refine the request. The **criteria** are multivalues fields extracted directly from the metadata each record:
 1. response status 
 2. month (extracted from date)
 3. site identifier (extracted from archiving session identifier)
 4. content type (5 normalized type extracted from MIME content-type)
 5. size category (8 discreet categories extracted from document size in bytes)
 6. top level domain (extracted from URL)
 7. depth

Using **criteria** and **features**, we are able to answer requests in the following form:
```
    What is the value of FEATURE-x when CRITERIA-a = X and CRITERIA-b = Y and [...] ?
```

### Real-time exploration

Short response-times are essential in effective exploration. When diagnosing a problem or exploring a dataset, users tend to make amore general requests first, and then refine and/or change the request step by step. For example:
> What is the biggest sites in the archive ?
> : `site X`
>
> For `site X`, what type of content represents the biggest size ?
> : `content type Y`
>
> What is the average size of `content type Y` on `site X` ?
> : `700KB`
>
> What is the average size of `content type Y` accros the whole archive ?
> : `50KB`

For this kind of back-and-forth conversation, short response-times help the user refine his request progressively while staying focused on the question rather than the underlying technicalities.

Considering the amount of records that are created every year in our archive (approximately *7 billion metadata records* in 2013), we cannot afford on-the-fly calculations to achieve reasonnable response-times. In early tests using [Apache Pig](http://pig.apache.org/), we experienced response-times averaging from several hours to several days. We quickly realized that for short response-times, we needed to **reduce** the amount of data on which we do requests, and also **pre-compute** features that we make available.


### MapReduce strategy for pre-computed results

The pre-computed backing data for our system is produced using the [Hadoop](http://hadoop.apache.org/) [MapReduce](http://en.wikipedia.org/wiki/MapReduce) framework. This framework enables us to scale the pre-computing process by splitting it into multiple small tasks and distributing these tasks across computers in a cluster.

Using MapReduce, we use the metadata as input to the [Mapper](./src/main/java/fr/ina/dlweb/proprioception/facetRedux/job/FacetReduxMapper.java#L71) to generate _two_ outputs for each metadata record. The ouput **key** contains a **prefix** (the *criteria* for the record) and a **suffix** (containing the _URL `MD5` in the first output_ or _the content `SHA256` in the second output_). The **value** contains the content size.
```javascript
// Mapper output
KEY: {
  // criteria 
  prefix: [ '[status]','[month]','[siteId]','[type]','[sizeCategory]','[tld]','[depth]' ],
  // suffixType: 'URL MD5' or 'Content SHA256'
  suffix: [ '[suffixType]', '[md5 or sha256]' ]
}
VALUE: [ '[size]' ]
```

The [Reducer](./src/main/java/fr/ina/dlweb/proprioception/facetRedux/job/FacetReduxReducer.java#L71) sorts the records by *key prefix* and *key suffix*. The records with the same *key prefix* are grouped, the key suffix is used to count **unique URLS** (using URL [MD5](http://en.wikipedia.org/wiki/MD5) signatures), **unique contents** (using content SHA256 signatures) and compute the **deduplicated size** (summing the size of unique contents). The Reducer generates _one input per unique key prefix_, the value of the output 
```javascript
// Reducer output
KEY: [ '[status]','[month]','[siteId]','[type]','[sizeCategory]','[tld]','[depth]' ]
VALUE: [ 'recordsCount', 'uniqueURLs', 'uniqueSHAs' 'cumulatedSize', 'deduplicatedSize' ]
```


#### First approach: enumerate criteria (TL;DR: not working)

A naive approach is to create just *one* key prefix for each metadata record. The following example illustrates the ouput of the Mapper for this approach (for the sake of conciseness, URL MD5 key suffixes have been omitted):
```javascript
// 1) an HTML page
{KEY:{
  prefix:{status:'ok', month:'2013-10', siteId:'foo', type:'HTML',  sizeCategory:'10k-150k', tld:"com", depth:0},
  suffix:{type:'sha256', sha256:'[X]'}
}, VALUE:{size:'73k'}}
// 2) the same image twice in the same month
{KEY:{
  prefix:{status:'ok', month:'2013-10', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:1},
  suffix:{type:'sha256', sha256:'[Y]'}
}, VALUE:{size:'120k'}}
{KEY:
  prefix:{status:'ok', month:'2013-10', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:1},
  suffix:{type:'sha256', sha256:'[Y]'}
}, VALUE:{size:'120k'}}
// 3) another image (different content signature) with the same key.
{KEY:
  prefix:{status:'ok', month:'2013-10', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:1},
  suffix:{type:'sha256', sha256:'[Z]'}
}, VALUE:{size:'45k'}}
// 4) the same image as in 1), but one month later
{KEY:
  prefix:{status:'ok', month:'2013-11', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:1},
  suffix:{type:'sha256', sha256:'[Y]'}
}, VALUE:{size:'120k'}}
```

These 4 entries will be sorted and handed to the Reducer. Consecutive entries with the same *key prefix* will be merged and unique content signatures counted. This would be the Reducer's output:
```javascript
{KEY:{status:'ok', month:'2013-10', siteId:'foo', type:'HTML',  sizeCategory:'10k-150k', tld:'com', depth:0},
 VALUE:{records:1, size:'73k', deduplicatedSize:'73k', uniqueSHAs:1, uniqueURLs:?}
}
{KEY:{status:'ok', month:'2013-10', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:1},
 VALUE:{records:3, size:'120k + 120k + 45k', deduplicatedSize:'120k + 45k', uniqueSHAs:2, uniqueURLs:?}
}
{KEY:{status:'ok', month:'2013-11', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:1},
 VALUE:{records:1, size:'120k', deduplicatedSize:'120k', uniqueSHAs:1, uniqueURLs:?}
}
```

With these results, to request the number of *records* for images on site `foo`, we simply need to sum the `records` field for all entries that match `siteId:'foo'` and `type:'IMAGE'`. This works because the `records` field is [associative](http://en.wikipedia.org/wiki/Associative_Property). On the other hand, if we want to count the number of unique *content signatures* for the same criteria, we cannot simply sum the `uniqueSHAs` field for matching records. If we did so, we would get `4` instead of `3`, because the image with content signature `sha256:[Y]` would have been counted twice (once in for `month:'2013-10'` and once for `month:'2013-11'`).


#### Actual approach: enumerate all criteria combinations

In this approach, we enumerate all possible combinaisons of *criteria* that can be made for an entry and pre-compute the values of the *features* for each combination. Following the previous example, to correctly compute the value of the `uniqueSHAs` field, we would have needed to precompute a result with the following key:
```javascript
  {KEY:{status:'ANY', month:'ANY', siteId:'foo', type:'IMAGE', sizeCategory:'ANY', tld:'ANY', depth:'ANY'}
```

To be able to compute such a result, the Mapper and Reducer need to be redesigned. Specifically, for each ouput in the previously described Mapper, we will generate **all** possible combinations of *criteria* in the key prefix _where a criteria matches any value_ (i.e. `ANY`). 
To enumerate these [combinations with our 7 criteria](http://rosettacode.org/wiki/Combinations#Java), we generate what is called a [*C(n, k) for all k where `n=7`*](http://en.wikipedia.org/wiki/Combination#Number_of_k-combinations_for_all_k). To limit the number of possible combinations and improve scalability, we have decided to exclude the first *criteria* (`response status`) from the combinations. Thus, for each metadata entry, the Mapper will generate the *C(6, k) for all k* key combinations (64 combinations) of all *criteria* (see actual Java code for generating [boolean combinations](./src/main/java/fr/ina/dlweb/proprioception/facetRedux/job/FacetReduxJob.java#L117) and [key combinations](./src/main/java/fr/ina/dlweb/proprioception/facetRedux/job/FacetReduxMapper.java#L170)).
The following array represents the 64 generated combinations, where a *criteria* is replaced by `*` when it can match any value.
```
01  02  03  04  05  06  07  08  09  10  11  12  13  14  15  16  17  18  19  20  21  22  23  24  25  26  27  28  29  30  31  32  33  34  35  36  37  38  39  40  41  42  43  44  45  46  47  48  49  50  51  52  53  54  55  56  57  58  59  60  61  62  63  64  
[1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] [1] [*] 
[2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] [2] [2] [*] [*] 
[3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] [3] [3] [3] [3] [*] [*] [*] [*] 
[4] [4] [4] [4] [4] [4] [4] [4] [*] [*] [*] [*] [*] [*] [*] [*] [4] [4] [4] [4] [4] [4] [4] [4] [*] [*] [*] [*] [*] [*] [*] [*] [4] [4] [4] [4] [4] [4] [4] [4] [*] [*] [*] [*] [*] [*] [*] [*] [4] [4] [4] [4] [4] [4] [4] [4] [*] [*] [*] [*] [*] [*] [*] [*] 
[5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [5] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] 
[6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [6] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*] [*]
```

As a result, the Mapper's output has the same *key suffixes* and *values* as in the previous approach, but there are 64 times more output records because of the different combinations of **key prefixes** generated for each record:
```javascript
{status:'ok', month:'2013-10', siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:'1'}
{status:'ok', month:'*'      , siteId:'foo', type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:'1'}
{status:'ok', month:'2013-10', siteId:'*'  , type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:'1'}
{status:'ok', month:'*'      , siteId:'*'  , type:'IMAGE', sizeCategory:'10k-150k', tld:'com', depth:'1'}
{status:'ok', month:'2013-10', siteId:'foo', type:'*'    , sizeCategory:'10k-150k', tld:'com', depth:'1'}
[...]
{status:'ok', month:'2013-10', siteId:'foo', type:'*'    , sizeCategory:'*'       , tld:'*'  , depth:'*'}
{status:'ok', month:'*'      , siteId:'foo', type:'*'    , sizeCategory:'*'       , tld:'*'  , depth:'*'}
{status:'ok', month:'2013-10', siteId:'*'  , type:'*'    , sizeCategory:'*'       , tld:'*'  , depth:'*'}
{status:'ok', month:'*'      , siteId:'*'  , type:'*'    , sizeCategory:'*'       , tld:'*'  , depth:'*'}
```






# [...]

