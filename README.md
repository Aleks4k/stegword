# Stegword

A desktop application written in **Clojure** for hiding encrypted text messages inside PNG images using LSB steganography and AES-GCM encryption.

## About the Project

Stegword is a student project focused on combining two concepts:

- **cryptography**, to ensure that hidden message remains unreadable without the correct password
- **steganography**, to hide existence of encrypted payload inside an image

After choosing a PNG file and defining a password, the application automatically "packs" your message into a new, encrypted image.
This application also supports extracting messages from previously processed images using same password that was used for encryption.

The focus of this project is complete **data confidentiality**.
Even assuming that the attacker notices the changes in the image, without the correct password the content remains absolutely inaccessible.

---

## Current Features

- Desktop GUI built with **Seesaw**
- PNG image selection through file chooser
- Password validation before encryption/decryption
- Message validation before encryption
- Optional GZIP compression when it reduces payload size
- **PBKDF2-HMAC-SHA256** master key derivation with per-message random salt
- Derivation of:
  - AES encryption key
  - AES-GCM nonce
  - position-selection key for random payload embedding
- **AES-GCM** encryption and authentication
- Custom binary header containing:
  - version
  - gzip flag
  - encrypted message length
  - salt
- Header written linearly into the beginning of the image
- Encrypted payload written into pseudo-random RGB channel positions
- Decryption support for previously encrypted images
- Automated tests for validation, crypto flow, stego logic and actions

---

## How It Works

### 1. Message Preparation
The input message is converted to UTF-8 bytes.  
The application also creates a GZIP-compressed version of the message and compares sizes.

- If the compressed version is smaller, it is used
- Otherwise, the original bytes are used

A gzip flag is stored in the header so decryption knows whether decompression is needed.

### 2. Key Derivation
A random 16-byte salt is generated for every encryption.

From `password + salt`, the application derives a master key using **PBKDF2-HMAC-SHA256**.  
From that master key, three separate values are derived:

- encryption key
- AES-GCM nonce
- random position-selection key

### 3. Encryption
The prepared message bytes are encrypted using **AES-GCM**.

This produces:
- ciphertext
- authentication tag

### 4. Header Construction
A fixed-size header is created and stored at the beginning of the image.

Header layout:

- **1 byte** → version (7 bits) + gzip flag (1 bit)
- **4 bytes** → encrypted message length
- **16 bytes** → salt

Total header size: **21 bytes = 168 bits**

### 5. Steganographic Embedding
The header is written linearly into the first RGB channel slots of the image.

The encrypted payload is then written bit by bit into pseudo-randomly chosen pixel channels.
The random selection depends on the password-derived position key, meaning that without the correct password, reconstructing the payload order is significantly harder.

Only the **least significant bit (LSB)** of RGB channels is modified.

### 6. Decryption
During decryption, the application:

1. reads the header from the image
2. extracts version, gzip flag, encrypted message length and salt
3. re-derives all required keys from password + salt
4. reconstructs the encrypted payload using the same pseudo-random channel selection logic
5. decrypts the payload with AES-GCM
6. decompresses it if the gzip flag is set

---

## Security Notes

This project is designed primarily to protect the **content of the message**, not to guarantee perfect undetectability.

Important notes:

- Without the original image, an attacker faces an extremely difficult task of extracting data without knowing the password.
- Having both files (original and modified) allows detection of changes, but the content itself remains unreadable.
- Strong encryption (AES-GCM), supported by derivation of the key from the password, guarantees that the message is secure even under scrutiny.
- The key link in the entire chain of defense is the complexity of the user's password - without it, all advanced algorithms lose their meaning.

This project should therefore be understood as:

**encrypted message hiding**, not perfect steganographic invisibility.

---

## Technologies Used

### Language and Runtime
- **Clojure**
- **JDK 21**

### GUI
- **Seesaw**

### Cryptography
- **PBKDF2-HMAC-SHA256**
- **AES-GCM**
- **HMAC-SHA256**

### Testing
- **clojure.test**

---

## Project Structure

```text
src/
  app/
    core.clj        ;; application entry point
    ui.clj          ;; GUI logic
    actions.clj     ;; high-level encrypt/decrypt flow
    crypto.clj      ;; PBKDF2, HKDF, AES-GCM, gzip helpers
    image.clj       ;; image loading/saving helpers
    stego.clj       ;; header, bit logic, pixel embedding/extraction
	validation.clj  ;; PNG, password and message validation

test/
  app/
	validation_test.clj
    crypto_test.clj
    image_test.clj
    stego_test.clj
    actions_test.clj
    test_runner.clj
```

---

## How to Run

From the project root:

```bash
clj -M -m app.core
```

This starts the desktop application.

---

## Running Tests

To run all project tests:

```bash
clj -A:test -M -m app.test-runner
```

The current version of the project relies on automated tests covering the following aspects:

- validation
- crypto roundtrip
- header roundtrip
- stego payload write/read logic
- full encrypt/decrypt flow

---

## Typical Usage

### Encrypting a Message
1. Start the application
2. Click **Encrypt**
3. Select a PNG image
4. Enter a valid password
5. Enter a message
6. Confirm capacity usage
7. A new encrypted PNG image is created

### Decrypting a Message
1. Start the application
2. Click **Decrypt**
3. Select a previously encrypted PNG image
4. Enter the correct password
5. The hidden message is displayed

---

## Project Status

Current state:
- encryption and decryption are implemented
- payload embedding and extraction are implemented
- project logic is separated into multiple namespaces
- automated tests are added

This project is considered a functional student-project prototype, not a production-ready security tool.

---

## Limitations

- Works only with **PNG** images
- Uses **LSB steganography**, which is not robust against image transformations
- Hidden data may be destroyed by:
  - converting PNG to JPG
  - resizing
  - cropping
  - reprocessing the image in external tools
- The project is optimized for educational clarity, not for maximum steganographic resistance

---

## Future Improvements

- Replace PBKDF2 with Argon2id
- Add authenticated header
- Improve steganographic resistance using adaptive embedding
- Add export or save option for decrypted messages
- Package the application as a more user-friendly distributable build

---

## Author

**Aleksa Čavić**  
Master Student of Faculty of Organizational Sciences
Software Engineering and Artificial Intelligence