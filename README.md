# Intruder: Directly write to remote managed heap via RDMA technology.
Use api similar to ObjectOutputStream to send objects, and ObjectInputStream to read objects. 
Sender directly write objects to remote host's managed heap to achieve truely zero-copying.
Serialization/deserialization is eliminated.
