### Retriever

Work in progress

Retriever is an open-source API (including a reference implementation) to efficiently
distribute content to the edge of a network. It can use conventional proxy caches and 
peer-to-peer sharing to retrieve content. The checksum of each piece of content is verified 
to ensure it has not been tampered with. 

Most large content distributed via https is not actually private (video, Javascript, images, etc). 
Using caches and/or peer-to-peer sharing can be highly effective to reduce bandwidth usage in situations 
where many devices on the same network want to access the same content (e.g. in education settings).

### JVM/Android

Retriever on JVM/Android uses the following logic to fetch a resource:

* If a proxy cache is provided using [Web Proxy Auto Discovery](https://en.wikipedia.org/wiki/Web_Proxy_Auto-Discovery_Protocol]) 
  (WPAD), route the request through the proxy.
* If another node (discovered using Network Service Discovery) has the content, download
  it from the other node.
* Otherwise, download the content from its original URL.

It's (almost) as simple to use as plain old http.  Now you can download from any local device that has the origin file 
as simply as making a plain http call:

```
val retriever = Retriever.Builder.build()
retriever.retrieve(RetrieverRequest(url = "https://myserver.com/somebigfile",
     //Alternatively: specify a url that contains the integrity in plain text 
     //e.g. integrity =  https://myserver.com/somebigfile.integrity
    integrity="sha384-q8i/X+965DzO0rT7abK41JStQIAqVgRVzpbzo5smXKp4YfRvH+8abtTE1Pi6jizo"))
  .saveToDir(File("/some/path"))
```

### Web Browsers

The security restrictions make peer discovery (seemingly) impossible. It should though be possible to use
native subresource integrity and url rewriting e.g. 
```
<img crossorigin="crossorigin"
     integrity="sha384-q8i/X+965DzO0rT7abK41JStQIAqVgRVzpbzo5smXKp4YfRvH+8abtTE1Pi6jizo"
     data-retriever-src="http://myserver.com/somebigpicture.png"/>
```

On the browser the following logic would be used:

* Check if a suitable proxy is available using a common subdomain (e.g. http://local.retriever-protocol.org). The 
  subdomain should be redirected to a compliant server by the network admin if they want resources to be loaded via 
  a local proxy to serve such resources.
* If the local proxy is available, then rewrite the request to http://local.retriever-protocol.org/singular?url=originalUrl .
* If the local proxy is not available, then use the original url as normal. 

### Security

The retriever API is designed to ensure efficient tamper-proof distribution of **public** resources (e.g. videos, 
Javascript, images, etc) to the edge of a network. It is not intended to be used for credentials or private data etc.
No attempt is made to hide the original URL being retrieved. Application developers are best placed to differentiate 
between network requests that fetch non-sensitive public information and those that transfer private information.

There is some 
trade-off between efficiency and privacy. A network administrator can already monitor what sites are being accessed (even
though https keeps the urls and content private). The administrator would be able to see the full resource urls when it
is accessed via retriever (using a proxy) instead of only being able to see the domain.  

### HTTP API

Check availability:

```
POST: /endpoint/availability
payload:
[ "https://myserver.com/somebigfile", "https://myserver.com/otherfile"]

response:
{
  {
     originUrl: "https://myserver.com/somebigfile",
     sha256: "abc",
     size: 12121  
  }
}
```

Retrieval:

```
GET /endpoint/singular?originUrl=https://myserver.com/otherfile

response: 
[data]

POST /endpoint/concatenated
payload:
["https://myserver.com/somebigfile", "https://myserver.com/otherfile"]

response:
zip containing each requested url (in the same order as requested)

```

### FAQs

Q) Why not use bittorrent?

A) That's what we thought at first. 
* Bittorrent can have difficulties with restrictive firewalls as it tries to traverse NAT. Retriever is 100% based on HTTP.
* Torrents are packaged together and downloading individual files is possible, but more complicated.
* Bittorrent traffic can be unpredictable. Retriever is designed to make it simple for developers and network 
admins to optimize routing using proven http proxy software that can be deployed for ISPs and Mobile Network Operators.
* Overall using bittorrent was far more complex than http. We wanted to make efficient content delivery using both peers
and proxies as simple as using normal http.

