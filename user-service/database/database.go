package database

import (
	"context"
	"errors"
	"fmt"
	"github.com/crgimenes/goconfig"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
	"log"
)


type MongoDB struct {
	Host string `cfg:"HASHLAB_MONGODB_HOST" cfgDefault:"localhost:27017" cfgRequired:"true"`
	Username string `cfg:"HASHLAB_MONGODB_USERNAME" cfgDefault:"hashlab" cfgRequired:"true"`
	Password string `cfg:"HASHLAB_MONGODB_PASSWORD" cfgDefault:"hashlab" cfgRequired:"true"`
	Database string `cfg:"HASHLAB_MONGODB_DATABASE" cfgDefault:"hashlab" cfgRequired:"true"`
	AuthSource string `cfg:"HASHLAB_MONGODB_AUTH_SOURCE" cfgDefault:"admin"`
}

func ConnectDB(m MongoDB) (*mongo.Database, error) {
	uri := fmt.Sprintf(`mongodb://%s:%s@%s/%s?&authSource=%s&authMechanism=SCRAM-SHA-1`,
		m.Username,
		m.Password,
		m.Host,
		m.Database,
		m.AuthSource,
	)

	client, err := mongo.NewClient(options.Client().ApplyURI(uri))
	if err != nil {
		return nil, fmt.Errorf("couldn't connect to mongo: %v", err)
	}

	ctx := context.Background()
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	err = client.Connect(ctx)
	if err != nil {
		return nil, fmt.Errorf("mongo client couldn't connect with background context: %v", err)
	}

	return client.Database(m.Database), nil
}

func SetupDB() (*mongo.Database, error) {
	config := MongoDB{}

	err := goconfig.Parse(&config)
	if err != nil {
		fmt.Println(err)
		return nil, errors.New("mongodb configuration not found")
	}

	db, err := ConnectDB(config)
	if err != nil {
		log.Printf("database configuration failed: %v", err)
		return nil, errors.New("client is disconnected")
	}

	return db, nil
}