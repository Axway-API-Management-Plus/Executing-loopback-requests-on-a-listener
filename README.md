# Description
The 'Call Internal Service' filter is a filter which is designed to hook a servlet application (static files or policies) with a top policy. 
To see this filter in action, you can load the Admin Node Manager configuration in the policy studio. 
The point in this filter is to use a 'loopback' message which is directly processed by the listener. 
Upon return, the loop back circuit path is then copied into the current message.

In our case we implement a loopback 'like' functionality:
- A request is received on a special prefixed path (Ex : '/prefix/api/...'),
- The prefix is removed and is saved in a HTTP header, then the request URI is modified without the prefix (Ex: '/api/...'),
- The request is then routed to the API Manager for further processing (using a Connect to URL).

The goal is to avoid Connect to URL to improve performance and scalability. Attached to this document you've got a script which take the current 'http.request.uri' to execute a loopback request.

## API Management Version Compatibilty
This artefact was successfully tested for the following versions:
- V7.4.1


## Install

```
• Modify 'http.request.uri' using standard 'Rewrite URL' filter
• Eventually, modify http verb/body and headers
```

## Usage

```
• Call the loopback script
```

## Bug and Caveats

```
This script SHOULD NOT be used more than once for a given message
```

## Contributing

Please read [Contributing.md](https://github.com/Axway-API-Management/Common/blob/master/Contributing.md) for details on our code of conduct, and the process for submitting pull requests to us.


## Team

![alt text][Axwaylogo] Axway Team

[Axwaylogo]: https://github.com/Axway-API-Management/Common/blob/master/img/AxwayLogoSmall.png  "Axway logo"


## License
[Apache License 2.0](/LICENSE)
