### Retriever

Work in progress

Retriever is an open-source HTTP API (including a reference implementation) to efficiently
distribute content to the edge of a network. It combines secure peer-to-peer resource 
sharing and proven https caching to dramatically reduce the bandwidth needed.

It's (almost) as simple to use as plain old http, but retriever can dramatically reduce bandwidth
needs by searching nearby devices (peers) and proxy caches (e.g. Squid or Apache Traffic Control).
This can be especially useful in situations where many devices on the same network want to
access the same content (e.g. in education settings). 

Now you can download from any local device that has the origin file as simply as making a plain http
call:

```
val retriever = Retriever.Builder.build()
retriever.retrieve(RetrieverRequest("https://myserver.com/somebigfile", 
        IgnoreChecksumProvider()))
  .saveToDir(File("/some/path"))
```

In reality, you should run a checksum to make sure the correct data was retrieved 


### HTTP API

Check availability:

```
POST: /endpoint/availability
payload:
{
   {
    sourceUrl: "https://myserver.com/somebigfile"
   },
   {
    sourceUrl: "https://myserver.com/otherfile"
   }
}

response:
{
  {
     sourceUrl: "https://myserver.com/somebigfile"
     sha256: "abc"
     size: 12121  
  }
}
```

Retrieval:

```
GET /endpoint/singular?url=https://myserver.com/otherfile

response: 
[data]

POST /endpoint/concatenated
payload:
["https://myserver.com/somebigfile", "https://myserver.com/otherfile"]

response:
Concatenated data

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

