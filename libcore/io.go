package libcore

import (
	"context"
	"io"
	"os"

	"github.com/codeclysm/extract"
	"github.com/ulikunitz/xz"
)

func Unxz(archive string, path string) error {
	i, err := os.Open(archive)
	if err != nil {
		return err
	}
	r, err := xz.NewReader(i)
	if err != nil {
		i.Close()
		return err
	}
	o, err := os.Create(path)
	if err != nil {
		i.Close()
		return err
	}
	_, err = io.Copy(o, r)
	i.Close()
	return err
}

func Unzip(archive string, path string) error {
	i, err := os.Open(archive)
	if err != nil {
		return err
	}
	defer i.Close()
	err = extract.Zip(context.Background(), i, path, nil)
	return err
}
