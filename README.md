FacetRedux
==========

**A proprioception tool for real-time big data exploration.**


FacetRedux is a tool developed and used at [Ina's Web Legal Deposit](http://www.institut-national-audiovisuel.fr/collecte-depot-legal-web.html) for crawl metadata data-mining. This tool enables us to explore statistical indicators about our archive in real-time. We use it on a day-to-day basis to have an overview of the contents we archive, in order to tune our collection tools and methods.

Propriocetion ?
--

Proprioception is a concept we borrowed to [cognitive science](http://en.wikipedia.org/wiki/Cognitive_science), where it refers to the intuitive perception we have of our own body. Proprioception explains how we are able to touch our nose without poking oursleves in the eyes : we constantly have an intuitive knowledge of the position our our and and nose.

This concept seemed relevent to us to describe our Web archive data-mining effort. As a matter of fact, one of the major challenges for a Web archive is to provide an analytical overview. On one hand, it is easy to request one of the billion documents from our archive, on the other hand, getting and answer to the following types of questions is more difficult:
 * "How did the number of distinct images collected on arte.tv evolve from April 2012 to April 2013?"
 * "What is the average size of a Flash animation collected in 2013?"
 * "What is the number of distinct URLs collected in the `.fr` TLD in 2013?"

Mining metadata
--

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

Responding to generic questions
--

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




