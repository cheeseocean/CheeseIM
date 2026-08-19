package main

import (
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/cheeseim/cheesebox/internal/devauth"
)

func main() {
	userID := flag.String("user", "", "assertion subject/user ID")
	issuer := flag.String("issuer", "cheeseim-account", "trusted issuer")
	audience := flag.String("audience", "cheeseim-im", "target audience")
	lifetime := flag.Duration("lifetime", time.Minute, "token lifetime, at most 1m")
	flag.Parse()
	secret := os.Getenv("CHEESEIM_LOGIN_ASSERTION_SECRET")
	assertion, err := devauth.GenerateAssertion(*userID, *issuer, *audience, secret, randomID(), time.Now(), *lifetime)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(assertion)
}

func randomID() string {
	value := make([]byte, 16)
	if _, err := rand.Read(value); err != nil {
		log.Fatal(err)
	}
	return hex.EncodeToString(value)
}
