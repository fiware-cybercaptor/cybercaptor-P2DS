**************
Architecture
**************

The SEPIA library has rather fixed ideas of how communication between
the various peers should happen, and it is unfortunately rather
difficult to untangle these from the basic protocols.

At its base, SEPIA creates a thread for every peer, and that thread
communicates with other peers through messages that signify events. When
a peer waits for a message, it blocks at a socket. Needless to say, this
is not nice when in the context of servlets. We solve this problem by
spawning a thread for every peer, and then connecting the input of the
thread to a blocking message queue. Whenever the servlet receives a
message that's for a particular thread, the servlet extracts the
message, verifies its signature (which has to be done separately from
SSL for techncial reasons) and puts it into the message queue.
