Bayebot
=======

Overview
--------
A proof-of-concept XMPP bot that applies a Bayesian filter to messages in a MUC and reports detected matches.

Quick Start
-----------
- Populate Messages-Spam.txt with known spam messages
- Populate Messages-KnownGood.txt with known good messages
- Populate Rooms.txt with rooms you want to monitor
- Populate Account.txt with first line JID and second line password for the bot account

Prebuilts
---------
- via CI: https://gitlab.com/divested/bayebot/-/jobs/artifacts/master/browse?job=build

TODO
----
- Retroactive learning from MAM based on flag users
- Ability to kick/ban repeat offenders

Credits
-------
- Tigase Java XMPP Client Library (AGPL-3.0), https://github.com/tigase/jaxmpp
- Classifier4J (Apache-1.1, with permission granted for use under Apache-2.0), http://classifier4j.sourceforge.net

Donate
-------
- https://divested.dev/donate
